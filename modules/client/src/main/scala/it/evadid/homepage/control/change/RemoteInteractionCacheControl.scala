package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.{RemoteHistoryCache, WorkbookSyncEnvironment}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

/** Signals replace environment bindings; departing bindings retain their own caches and IO. */
case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {
  private given ExecutionContext = ExecutionContext.global
  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private def attempt[A](operation: => Future[A]): Future[A] = Try(operation).fold(Future.failed, identity)
  private def settled(operations: Seq[Future[Unit]]): Future[Unit] =
    Future.sequence(operations.map(_.transform(result => Success(result)))).flatMap(results =>
      results.collectFirst { case scala.util.Failure(error) => error }.fold(Future.unit)(Future.failed))

  private case class UserSyncState(reset: SyncResetState = SyncResetState.Idle, pending: Set[Future[Unit]] = Set.empty)

  /** Reset spans workbooks, but never follows an operation into another user's binding. */
  private class UserBinding {
    val state = Var(UserSyncState())
    def enabled: Boolean = state.now().reset == SyncResetState.Idle
    def track(operation: Future[Unit]): Future[Unit] = {
      state.update(current => current.copy(pending = current.pending + operation))
      operation.onComplete(_ => state.update(current => current.copy(pending = current.pending - operation)))
      operation
    }
  }

  private val userBinding: StrictSignal[UserBinding] = fullInfo.signals.user
    .map(_.map(_.user.personId)).distinct.map(_ => new UserBinding).observe(using unsafeWindowOwner)
  val resetState: StrictSignal[SyncResetState] = userBinding.flatMapSwitch(_.state.signal.map(_.reset)).observe(using unsafeWindowOwner)

  private trait Restoration {
    def publish(context: ExecutionContextInformation): Unit
  }
  private case class TypedRestoration[F <: InteractionFormat](variable: InteractionVariable[F], history: InteractionVariableHistory[F]) extends Restoration {
    def publish(context: ExecutionContextInformation): Unit = variable.restoreHistory(syncLogger, history, context)
  }

  private trait InteractionSnapshot {
    def store(): Future[Unit]
    def fetch(): Future[Unit]
  }
  private trait InteractionBinding {
    def variable: InteractionVariable[? <: InteractionFormat]
    def snapshot: InteractionSnapshot
    def restoration: EventStream[Restoration]
    def report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]]
  }

  private class TypedBinding[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], environment: WorkbookSyncEnvironment,
      subscriptions: ManualOwner) extends InteractionBinding {
    val variable: InteractionVariable[F] = interaction.interactionVariable
    variable.bindFeedbackSession(fullInfo.feedbackSession.now())
    private val key = environment.usageContext.toSyncContext(variable.keyForSerialization)
    private val destinations = environment.syncDestinations.map(information => FormattedSyncDestination(information, interaction.interactionFormat))
    // One immutable snapshot holds the local history and location -> confirmed cache state.
    private val cacheState = Var(RemoteHistoryCache.CacheState.empty[SyncContext, F, FormattedSyncDestination[F]](destinations))
    private val cache = new RemoteHistoryCache(syncLogger,
      destinations.map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap,
      fromAirstreamVarToState(cacheState, subscriptions))

    def snapshot: InteractionSnapshot = {
      val local = Map(key -> variable.currentHistory)
      new InteractionSnapshot {
        def store(): Future[Unit] = cache.requestStoreToAll(local)
        def fetch(): Future[Unit] = {
          cache.mergeLocal(local)
          settled(destinations.toSeq.map(destination =>
            cache.refreshFrom(destination, Set(key), LocalDateTime.now().minusMinutes(5)).map(_ => ())))
        }
      }
    }
    /** The merged history, published only when it changes: every poll updates this interaction's cache state. */
    val restoration: EventStream[Restoration] = cacheState.signal.map(_.histories.get(key)).distinct.changes
      .collect { case Some(history) => TypedRestoration(variable, history) }
    val report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
      cache.reports.toSignal(Map.empty).map(_.get(key).map(current =>
        InteractionVariableSyncReport(destinations.map(_.information), current.syncedDestinations.map(_.information))))
  }

  /** No active/closing flags: the outer signal selects whose publications reach the workbook. */
  private class WorkbookBinding(val environment: Option[WorkbookSyncEnvironment], val user: UserBinding) {
    private val subscriptions = new ManualOwner
    val context: ExecutionContextInformation = fullInfo.workbookContextInformation
    val interactions: Seq[InteractionBinding] = environment.toSeq.flatMap(owner =>
      owner.interactions.toSeq.map(interaction => new TypedBinding(interaction, owner, subscriptions)))
    // Merged, not combined: a combined signal re-published every interaction whenever one of them changed.
    val restorations: EventStream[Restoration] = EventStream.mergeSeq(interactions.map(_.restoration))

    def flush(): Unit = environment.foreach(_.syncDestinations.foreach(_.handler.flush(true)))
    def detach(): Unit = {
      // Detaching observers does not cancel requests. Their captured handlers still flush normally.
      subscriptions.killSubscriptions()
      try flush() catch case scala.util.control.NonFatal(error) => syncLogger.logExceptionWarn("Could not flush the previous workbook", error)
    }
    def batch[A](selected: Seq[A], forceFlush: Boolean)(operation: A => Future[Unit]): Future[Unit] =
      if !user.enabled then Future.unit else {
        val operations = selected.map(binding => attempt(operation(binding)))
        val flushing = if forceFlush then attempt { flush(); Future.unit } else Future.unit
        user.track(settled(operations :+ flushing))
      }
    def phases(forceFlush: Boolean)(first: InteractionSnapshot => Future[Unit], second: InteractionSnapshot => Future[Unit]): Future[Unit] = {
      val captured = if user.enabled then interactions.map(_.snapshot) else Seq.empty
      batch(captured, forceFlush)(first).transformWith(before =>
        batch(captured, forceFlush)(second).transform(after => before.flatMap(_ => after)))
    }
  }

  private final case class DeferredStore(completion: Promise[Unit], run: () => Future[Unit])
  private val deferredStores = Var(Map.empty[(WorkbookBinding, String), DeferredStore])

  private val environments = fullInfo.signals.syncEnvironmentSignal.combineWith(userBinding).observe(using unsafeWindowOwner)
  private val binding: StrictSignal[WorkbookBinding] = environments.changes
    .scanLeft(new WorkbookBinding(environments.now()._1, environments.now()._2)) { (previous, next) =>
      val replacement = new WorkbookBinding(next._1, next._2)
      deferredStores.now().keys.filter(_._1 eq previous).foreach(runDeferred)
      previous.detach()
      replacement
    }.observe(using unsafeWindowOwner)

  // Switching subscriptions prevents old reads from publishing into a replacement workbook.
  binding.flatMapSwitch(current => current.restorations.map(update => (current, update)))
    .foreach { (current, update) =>
      // Each restoration is isolated: one interaction that cannot be restored never blocks the others.
      // (Not `Try(...).failed`: on success that allocates an exception, for every interaction on every poll.)
      if current.user.enabled then
        try update.publish(current.context)
        catch case scala.util.control.NonFatal(error) => syncLogger.logExceptionWarn("Could not restore an interaction history", error)
    }(using unsafeWindowOwner)

  override def storeAll(forceFlush: Boolean): Future[Unit] = {
    val current = binding.now()
    current.batch(current.interactions.map(_.snapshot), forceFlush)(_.store())
  }
  override def fetchAll(forceFlush: Boolean): Future[Unit] = {
    val current = binding.now()
    current.batch(current.interactions.map(_.snapshot), forceFlush)(_.fetch())
  }
  /** History updates return to the UI at once: the cache is compared and written after the current task.
    * Updates of one interaction before that point (an answer and its grading starts) share one store. The store
    * belongs to the binding of the update; when that binding is replaced first, it runs before the final flush.
    */
  override def store[F <: InteractionFormat](variable: InteractionVariable[F], forceFlush: Boolean): Future[Unit] = {
    val requested = binding.now()
    if forceFlush then storeNow(requested, variable, forceFlush)
    else {
      // Colliding interaction IDs of another workbook never join this binding's pending store.
      val pending = (requested, variable.keyForSerialization)
      deferredStores.now().get(pending).fold {
        val deferred = DeferredStore(Promise[Unit](), () => storeNow(requested, variable, forceFlush = false))
        deferredStores.update(_.updated(pending, deferred))
        afterCurrentTask().foreach(_ => runDeferred(pending))
        deferred.completion.future
      }(_.completion.future)
    }
  }

  /** Runs a pending store once: when it is due or, earlier, when its binding is replaced. */
  private def runDeferred(pending: (WorkbookBinding, String)): Unit =
    deferredStores.now().get(pending).foreach { deferred =>
      deferredStores.update(_ - pending)
      deferred.completion.completeWith(attempt(deferred.run()))
    }

  override def synchronize[F <: InteractionFormat](variable: InteractionVariable[F]): Future[Unit] = {
    val current = binding.now()
    val selected = current.interactions.filter(_.variable eq variable).map(_.snapshot)
    current.batch(selected, forceFlush = true)(_.store()).transformWith(stored =>
      current.batch(selected, forceFlush = true)(_.fetch()).transform(fetched => stored.flatMap(_ => fetched)))
  }

  private def storeNow[F <: InteractionFormat](current: WorkbookBinding, variable: InteractionVariable[F], forceFlush: Boolean): Future[Unit] =
    current.batch(current.interactions.filter(_.variable eq variable).map(_.snapshot), forceFlush)(_.store())

  private def afterCurrentTask(): Future[Unit] = {
    val due = Promise[Unit]()
    scala.scalajs.js.timers.setTimeout(0)(due.success(()))
    due.future
  }

  // A phase chain must retain its original binding even if the selected user/workbook changes between phases.
  override def fetchAndStore(forceFlush: Boolean): Future[Unit] = binding.now().phases(forceFlush)(_.fetch(), _.store())
  override def storeAndFetch(forceFlush: Boolean): Future[Unit] = binding.now().phases(forceFlush)(_.store(), _.fetch())
  override def ensureSynced(): Future[Unit] = resetState.now() match {
    case SyncResetState.Running(completion) => completion
    case SyncResetState.Idle => fetchAndStore(true)
    case _ => Future.unit
  }

  def resetAllData(): Future[Unit] = {
    val current = binding.now()
    val user = current.user
    user.state.now().reset match {
      case SyncResetState.Running(completion) => completion
      case _ =>
        val destinations = current.environment.map(_.syncDestinations.toSeq)
          .getOrElse(fullInfo.homepageInfoNow().syncDestinations(fullInfo))
        val completion = Promise[Unit]()
        user.state.update(_.copy(reset = SyncResetState.Running(completion.future)))
        fullInfo.invalidateFeedbackSession()
        current.interactions.foreach(_.variable.resetForNewSession())
        val draining = user.state.now().pending.toSeq.map(_.recover { case scala.util.control.NonFatal(_) => () })
        val reset = attempt {
          destinations.foreach(_.handler.flush(true))
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

  def createReportFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
    binding.flatMapSwitch(current => current.interactions.find(_.variable eq interaction.interactionVariable)
      .fold(Signal.fromValue(Option.empty[InteractionVariableSyncReport[SyncInformationWithContext]]))(_.report))
      .combineWith(resetState).map((report, reset) => report.map(current =>
        if reset == SyncResetState.Idle then current else current.copy(destinationsWithSufficientWrites = Set.empty)))
}
