package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import com.raquo.laminar.keys.EventProp
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.control.model.{BindingEnvironment, FullInfo}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.binding.{InteractionBinding, SyncConnection}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncPhases.{attempt, inOrder, settled}
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport
import org.scalajs.dom
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** Binds every interaction of the open workbook and keeps the bindings in step with the page's environment:
  *
  *  - a changed sync context (workbook, user, or a full reset) retires the bindings, sending their pending stores,
  *    and binds the interactions anew from their default answers;
  *  - a changed connection (credentials, destinations) reconnects the same bindings, which keep their histories.
  *
  * The bindings of one connection share its buffered destinations: a phase registers the requests of every
  * interaction before one flush. Retired bindings keep their own history and connection, so their late results
  * reach neither a replacement nor the page. Reset progress belongs to the user and spans workbooks.
  */
final class BindingController(fullInfo: FullInfo) {
  private given ExecutionContext = ExecutionContext.global
  def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private type AnyBinding = InteractionBinding[? <: InteractionFormat]

  private case class UserSyncState(reset: SyncResetState = SyncResetState.Idle, pending: Set[Future[Unit]] = Set.empty)

  /** Reset spans workbooks, but never follows an operation into another user's bindings. */
  private final class UserBinding {
    val state = Var(UserSyncState())
    def enabled: Boolean = state.now().reset == SyncResetState.Idle
    def track(operation: Future[Unit]): Future[Unit] = {
      state.update(current => current.copy(pending = current.pending + operation))
      operation.onComplete(_ => state.update(current => current.copy(pending = current.pending - operation)))
      operation
    }
  }

  private val userBinding: StrictSignal[UserBinding] = fullInfo.signals.user
    .map(_.map(_.user.personId)).distinct.map(_ => new UserBinding).observe(using fullInfo.owner)
  val resetState: StrictSignal[SyncResetState] = userBinding.flatMapSwitch(_.state.signal.map(_.reset)).observe(using fullInfo.owner)

  /** The bindings of one environment. A new generation of the same environment (a full reset) is a new context. */
  private final case class BindingSet(environment: BindingEnvironment, user: UserBinding, generation: Int,
      bindings: Seq[AnyBinding], observed: Seq[it.evadid.core.datastructures.state.Subscription[?]]) {
    def connection: SyncConnection = environment.connection

    def keepsContextOf(next: Selection): Boolean = environment.usageContext == next.environment.usageContext &&
      environment.workbook.zip(next.environment.workbook).forall((a, b) => a eq b) &&
      environment.workbook.isDefined == next.environment.workbook.isDefined && (user eq next.user) && generation == next.generation

    def batch(selected: Seq[AnyBinding], forceFlush: Boolean)(operation: AnyBinding => Future[Unit]): Future[Unit] =
      if !user.enabled then Future.unit else {
        val operations = selected.map(binding => attempt(operation(binding)))
        val flushing = if forceFlush then attempt { connection.flush(); Future.unit } else Future.unit
        user.track(settled(operations :+ flushing))
      }

    def phases(forceFlush: Boolean)(first: AnyBinding => Future[Unit], second: AnyBinding => Future[Unit]): Future[Unit] = {
      val captured = if user.enabled then bindings else Seq.empty
      inOrder(batch(captured, forceFlush)(first), batch(captured, forceFlush)(second))
    }
  }

  private final case class Selection(environment: BindingEnvironment, user: UserBinding, generation: Int)

  private val generation = Var(0)
  private val pendingStores = Var(Set.empty[AnyBinding])

  private val selection: StrictSignal[Selection] = fullInfo.signals.bindingEnvironment.combineWith(userBinding, generation.signal)
    .map(Selection.apply).observe(using fullInfo.owner)

  private val current: StrictSignal[BindingSet] = selection.changes
    .scanLeft(bindInteractions(selection.now()))((previous, next) =>
      if previous.keepsContextOf(next) then updateConnection(previous, next.environment) else changeContext(previous, next))
    .observe(using fullInfo.owner)

  /** New credentials or destinations for the same context: pending stores still use the previous connection,
    * which is then flushed; the bindings and their histories stay.
    */
  private def updateConnection(previous: BindingSet, environment: BindingEnvironment): BindingSet = {
    runPendingStores(previous)
    previous.bindings.foreach(_.connect(environment.connection))
    flush(previous.connection)
    previous.copy(environment = environment)
  }

  /** Retires the bindings after their pending stores and binds the interactions of `next` from default answers. */
  private def changeContext(previous: BindingSet, next: Selection): BindingSet = {
    previous.observed.foreach(_.cancel())
    runPendingStores(previous)
    flush(previous.connection)
    bindInteractions(next)
  }

  /** Each binding stores its changes after the current task; changes until then share one store. */
  private def bindInteractions(next: Selection): BindingSet = {
    val bindings: Seq[AnyBinding] = next.environment.interactions.map(_.interactionVariable.bindTo(next.environment.usageContext, next.environment.connection))
    val observed = bindings.map { binding =>
      val initial = binding.currentHistory
      binding.history.addObserver(history => if history != initial then scheduleStore(binding))
    }
    BindingSet(next.environment, next.user, next.generation, bindings, observed)
  }

  private def flush(connection: SyncConnection): Unit =
    Try(connection.flush()).failed.foreach(error => syncLogger.logExceptionWarn("Could not send buffered histories", error))

  private def scheduleStore(binding: AnyBinding): Unit = if !pendingStores.now().contains(binding) then {
    pendingStores.update(_ + binding)
    scala.scalajs.js.timers.setTimeout(0)(runPendingStores(current.now(), Set(binding)))
  }

  /** Runs the pending stores of `set` (of `only`, if given) once: when due or, earlier, when `set` is replaced. */
  private def runPendingStores(set: BindingSet, only: Set[AnyBinding] = Set.empty): Unit = {
    val due = pendingStores.now().filter(binding => set.bindings.contains(binding) && (only.isEmpty || only(binding)))
    if due.nonEmpty then {
      pendingStores.update(_ -- due)
      set.batch(due.toSeq, forceFlush = false)(_.store())
    }
  }

  /** A hidden page may be closed without running another task: pending stores would never start and buffered
    * destinations never send. So when the page is hidden or left, pending stores run at once and every destination
    * sends its buffer. The listeners sit on the window: `visibilitychange` is fired at the document and bubbles to
    * the window, so in the same dispatch they follow the editors, which write their typed text at the document.
    */
  private def storePendingNow(): Unit = {
    runPendingStores(current.now())
    flush(current.now().connection)
  }
  windowEvents(_ => BindingController.onVisibilityChange).filter(_ => dom.document.hidden)
    .foreach(_ => storePendingNow())(using fullInfo.owner)
  windowEvents(_.onPageHide).foreach(_ => storePendingNow())(using fullInfo.owner)

  def storeAll(forceFlush: Boolean = false): Future[Unit] = {
    val set = current.now()
    set.batch(set.bindings, forceFlush)(_.store())
  }
  def fetchAll(forceFlush: Boolean = false): Future[Unit] = {
    val set = current.now()
    set.batch(set.bindings, forceFlush)(_.fetch())
  }

  // A phase chain keeps its bindings even if the selected user or workbook changes between phases.
  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = current.now().phases(forceFlush)(_.fetch(), _.store())
  def storeAndFetch(forceFlush: Boolean = false): Future[Unit] = current.now().phases(forceFlush)(_.store(), _.fetch())

  /** Reads and writes everything with forced flushes; during a reset, waits for it instead. */
  def ensureSynced(): Future[Unit] = resetState.now() match {
    case SyncResetState.Running(completion) => completion
    case SyncResetState.Idle => fetchAndStore(true)
    case _ => Future.unit
  }

  /** Explicit synchronization of one interaction (e.g. its sync icon): writes and flushes its pending events,
    * then reads it back, since only events read from a destination count as synchronized.
    */
  def synchronize[F <: InteractionFormat](variable: InteractionVariable[F]): Future[Unit] = {
    val set = current.now()
    val selected = set.bindings.filter(_ eq variable.binding)
    inOrder(set.batch(selected, forceFlush = true)(_.store()), set.batch(selected, forceFlush = true)(_.fetch()))
  }

  /** Deletes the user's histories in every configured destination. The interactions are bound anew from their
    * default answers at once; until the reset has succeeded, synchronization stays paused for this user.
    */
  def resetAllData(): Future[Unit] = {
    val set = current.now()
    val user = set.user
    user.state.now().reset match {
      case SyncResetState.Running(completion) => completion
      case _ =>
        val destinations = set.connection.destinations.toSeq
        val completion = Promise[Unit]()
        user.state.update(_.copy(reset = SyncResetState.Running(completion.future)))
        generation.update(_ + 1)
        val draining = user.state.now().pending.toSeq.map(_.recover { case scala.util.control.NonFatal(_) => () })
        val reset = attempt {
          set.connection.flush()
          Future.sequence(draining).flatMap(_ => settled(destinations.map(destination =>
            attempt(destination.handler.clearAllHistories(destination.usageContext)).map(_ => ()))))
        }
        reset.onComplete { result =>
          user.state.update(_.copy(reset = result.fold(SyncResetState.Failed(_), _ => SyncResetState.Completed)))
          completion.tryComplete(result)
        }
        completion.future
    }
  }

  /** The sync report of the interaction's current binding; no destination counts as confirmed during a reset. */
  def reportFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[InteractionVariableSyncReport[SyncInformationWithContext]] =
    interaction.interactionVariable.bindingSignal
      .flatMapSwitch(binding => binding.report.toSignal(InteractionVariableSyncReport(binding.connection.destinations, Set.empty)))
      .combineWith(resetState).map((report, reset) =>
        if reset == SyncResetState.Idle then report else report.copy(destinationsWithSufficientWrites = Set.empty))
}

object BindingController {
  /** `visibilitychange` on the window, where it arrives after the document's listeners. */
  private val onVisibilityChange = new EventProp[dom.Event]("visibilitychange")
}
