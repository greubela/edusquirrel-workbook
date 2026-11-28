package it.evadid.homepage.control.change

import com.raquo.airstream.state.{LazyDerivedVar, LazyStrictSignal}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.keys.EventProp
import it.evadid.core.datastructures.state.StateHelper.fromAirstreamVarToState
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.state.{InteractionController, InteractionInfo, InteractionScope}
import it.evadid.workbook.interaction.sync.SyncPhases.{attempt, inOrder, settled}
import it.evadid.workbook.interaction.sync.{SyncInformationWithContext, SyncResetState, UpdateImportance}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.meta.InteractionVariableSyncReport
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** Every change of the page's answers, held in `interactionInfoVar`: the core [[InteractionController]] transitions,
  * Airstream views for renderers and editors, and the browser's synchronization.
  *
  * A changed history is stored after the current task, so changes made together (an answer and its grading starts)
  * share one store; the bindings of one connection share its buffered destinations. Synchronization phases keep the
  * state they started with. A reset pauses synchronization of its user, across workbooks, until it has succeeded.
  */
final class InteractionControl(interactionInfoVar: Var[InteractionInfo], owner: Owner)
    extends InteractionController(fromAirstreamVarToState(interactionInfoVar, owner)) {
  private given ExecutionContext = ExecutionContext.global
  private type AnyInteraction = WorkbookInteractionElement[? <: InteractionFormat]

  // VIEWS

  /** A deduplicated view of the state, current also while nothing observes it. */
  private final class Projection[A](project: InteractionInfo => A) extends LazyStrictSignal[A, A](
    interactionInfoVar.signal.map(project).distinct, identity, "InteractionInfo", ".projection") {
    override def tryNow(): Try[A] = Try(project(interactionInfoVar.now()))
  }

  def historySignal[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[InteractionVariableHistory[F]] =
    Projection(_.historyOf(interaction))

  def answerSignal[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[InteractionFormat.State[F]] = answerProjection(interaction)

  private def answerProjection[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]) = Projection(_.currentValueOf(interaction))

  /** The answer for an editor: each write is a user change whose importance `relevance` decides. */
  def boundVar[F <: InteractionFormat](interaction: WorkbookInteractionElement[F],
      relevance: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance): Var[InteractionFormat.State[F]] =
    new LazyDerivedVar[InteractionInfo, InteractionFormat.State[F]](interactionInfoVar, answerProjection(interaction),
      (current, value) => orUnchanged(current, interaction, current.withUserChange(interaction, value, relevance)), ".answer")

  def boundVar[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], importance: UpdateImportance): Var[InteractionFormat.State[F]] =
    boundVar(interaction, _ => importance)

  // USER-SCOPED SYNCHRONIZATION

  private case class UserSyncState(reset: SyncResetState = SyncResetState.Idle, pending: Set[Future[Unit]] = Set.empty)
  private val users = Var(Map.empty[String, UserSyncState])
  private def userOf(state: InteractionInfo): String = state.usageContext.userId
  private def stateOf(user: String): UserSyncState = users.now().getOrElse(user, UserSyncState())
  private def updateUser(user: String)(transition: UserSyncState => UserSyncState): Unit =
    users.update(all => all.updated(user, transition(all.getOrElse(user, UserSyncState()))))

  /** The reset progress of the current user. */
  val resetState: StrictSignal[SyncResetState] = interactionInfoVar.signal.map(userOf).distinct.combineWith(users.signal)
    .map((user, all) => all.get(user).fold(SyncResetState.Idle)(_.reset)).observe(using owner)

  private def track(user: String, operation: Future[Unit]): Future[Unit] = {
    updateUser(user)(current => current.copy(pending = current.pending + operation))
    operation.onComplete(_ => updateUser(user)(current => current.copy(pending = current.pending - operation)))
    operation
  }

  /** Runs `operation` for `keys` of `state`, then flushes if forced; nothing runs while the user's reset is pending. */
  private def batch(state: InteractionInfo, keys: Seq[String], forceFlush: Boolean)(operation: String => Future[Unit]): Future[Unit] = {
    val user = userOf(state)
    if stateOf(user).reset != SyncResetState.Idle then Future.unit else {
      val operations = keys.map(key => attempt(operation(key)))
      val flushing = if forceFlush then attempt { state.connection.flush(); Future.unit } else Future.unit
      track(user, settled(operations :+ flushing))
    }
  }

  // DEFERRED STORES

  private val pendingStores = Var(Set.empty[(InteractionInfo.Session, String)])

  override protected def requestStore(key: String): Unit = {
    val pending = (info.session, key)
    if !pendingStores.now().contains(pending) then {
      pendingStores.update(_ + pending)
      scala.scalajs.js.timers.setTimeout(0)(runPendingStores(Set(pending)))
    }
  }

  /** Runs pending stores of the current session once: when due or, earlier, before it is replaced or reconnected. */
  private def runPendingStores(only: Set[(InteractionInfo.Session, String)]): Unit = {
    val current = info
    val due = pendingStores.now().intersect(only)
    if due.nonEmpty then {
      pendingStores.update(_ -- due)
      batch(current, due.filter(_._1 == current.session).map(_._2).toSeq, forceFlush = false)(storeEntry(_, state = current))
    }
  }

  /** Runs the pending stores and sends every buffered request of the current connection. */
  def sendPending(): Unit = {
    runPendingStores(pendingStores.now())
    Try(flush()).failed.foreach(error => logger.logExceptionWarn("Could not send buffered histories", error))
  }

  /** A hidden page may be closed without running another task: pending stores would never start and buffered
    * destinations never send. So when the page is hidden or left, both happen at once. The listeners sit on the
    * window: `visibilitychange` is fired at the document and bubbles to the window, so in the same dispatch they
    * follow the editors, which write their typed text at the document.
    */
  windowEvents(_ => InteractionControl.onVisibilityChange).filter(_ => dom.document.hidden).foreach(_ => sendPending())(using owner)
  windowEvents(_.onPageHide).foreach(_ => sendPending())(using owner)

  // PHASES
  // Each phase starts after the current Airstream transaction, so it sees the changes made in it. Its entries
  // register their requests with the shared buffers, which the phase flushes once when forced.

  private def afterTransaction(operation: => Future[Unit]): Future[Unit] = Future.unit.flatMap(_ => operation)

  def storeAll(forceFlush: Boolean = false): Future[Unit] = afterTransaction {
    val state = info
    batch(state, state.entries.keys.toSeq, forceFlush)(storeEntry(_, state = state))
  }

  def fetchAll(forceFlush: Boolean = false): Future[Unit] = afterTransaction {
    val state = info
    batch(state, state.entries.keys.toSeq, forceFlush)(fetchEntry(_, state = state))
  }

  /** Reads, then writes, the state current at the start, even if another state is selected meanwhile. */
  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = afterTransaction {
    val state = info
    val keys = if stateOf(userOf(state)).reset == SyncResetState.Idle then state.entries.keys.toSeq else Nil
    inOrder(batch(state, keys, forceFlush)(fetchEntry(_, state = state)), batch(state, keys, forceFlush)(storeEntry(_, state = state)))
  }

  /** Reads and writes everything with forced flushes; during a reset, waits for it instead. */
  def ensureSynced(): Future[Unit] = resetState.now() match {
    case SyncResetState.Running(completion) => completion
    case SyncResetState.Idle => fetchAndStore(forceFlush = true)
    case _ => Future.unit
  }

  /** Explicit synchronization of one interaction (e.g. its sync icon): writes and flushes its pending events,
    * then reads it back, since only events read from a destination count as synchronized.
    */
  def synchronize(interaction: AnyInteraction): Future[Unit] = afterTransaction {
    val state = info
    val keys = Seq(interaction.keyForSerialization).filter(_ => state.contains(interaction))
    inOrder(batch(state, keys, forceFlush = true)(storeEntry(_, state = state)), batch(state, keys, forceFlush = true)(fetchEntry(_, state = state)))
  }

  /** Deletes the user's histories in every destination of the current connection, across workbooks. The answers are
    * bound anew from their defaults at once; until the reset has succeeded, synchronization stays paused for the user.
    */
  def resetAllData(): Future[Unit] = {
    val state = info
    val user = userOf(state)
    stateOf(user).reset match {
      case SyncResetState.Running(completion) => completion
      case _ =>
        val completion = Promise[Unit]()
        updateUser(user)(_.copy(reset = SyncResetState.Running(completion.future)))
        bind(InteractionScope(state.usageContext, state.interactions, state.connection))
        val draining = stateOf(user).pending.toSeq.map(_.recover { case scala.util.control.NonFatal(_) => () })
        val reset = attempt {
          state.connection.flush()
          Future.sequence(draining).flatMap(_ => settled(state.connection.destinations.toSeq.map(destination =>
            attempt(destination.handler.clearAllHistories(destination.usageContext)).map(_ => ()))))
        }
        reset.onComplete { result =>
          updateUser(user)(_.copy(reset = result.fold(SyncResetState.Failed(_), _ => SyncResetState.Completed)))
          completion.tryComplete(result)
        }
        completion.future
    }
  }

  /** The sync report of `interaction`; no destination counts as confirmed during a reset. */
  def reportSignal(interaction: AnyInteraction): Signal[InteractionVariableSyncReport[SyncInformationWithContext]] =
    Projection(_.reportOf(interaction)).combineWith(resetState).map((report, reset) =>
      if reset == SyncResetState.Idle then report else report.copy(destinationsWithSufficientWrites = Set.empty))
}

object InteractionControl {
  /** `visibilitychange` on the window, where it arrives after the document's listeners. */
  private val onVisibilityChange = new EventProp[dom.Event]("visibilitychange")
}
