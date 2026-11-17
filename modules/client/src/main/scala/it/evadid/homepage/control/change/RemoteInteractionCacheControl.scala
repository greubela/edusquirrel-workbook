package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.{RemoteHistoryCache, WorkbookSyncEnvironment}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.interaction.variable.events.InteractionEvents
import it.evadid.workbook.meta.InteractionVariableSyncReport
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** The active user/workbook environment owns typed caches, batched IO and reset state. */
case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {
  private given ExecutionContext = ExecutionContext.global
  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger
  private val resetting = Var[SyncResetState](SyncResetState.Idle)
  val resetState: StrictSignal[SyncResetState] = resetting.signal
  private val pending = Var(Set.empty[Future[Unit]])
  private def enabled: Boolean = resetting.now() == SyncResetState.Idle
  private def environment = fullInfo.signals.syncEnvironmentSignal.now()

  private trait Binding {
    def variable: InteractionVariable[? <: InteractionFormat]
    def store(): Future[Unit]
    def fetch(): Future[Unit]
    def report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]]
  }

  private class TypedBinding[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], owner: WorkbookSyncEnvironment) extends Binding {
    val variable: InteractionVariable[F] = interaction.interactionVariable
    variable.bindFeedbackSession(fullInfo.feedbackSession.now())
    private val key = owner.usageContext.toSyncContext(variable.keyForSerialization)
    private val destinations = owner.syncDestinations.map(information => FormattedSyncDestination(information, interaction.interactionFormat))
    private val cache = RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]](syncLogger,
      destinations.map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap)
    private def active: Boolean = enabled && environment.contains(owner)

    def store(): Future[Unit] =
      if !active then Future.unit else cache.requestStoreToAll(Map(key -> variable.currentHistory))

    def fetch(): Future[Unit] = {
      if !active then Future.unit else {
        cache.mergeLocal(Map(key -> variable.currentHistory))
        Future.traverse(destinations) { destination =>
          cache.refreshFrom(destination, Set(key), LocalDateTime.now().minusMinutes(5)).map { received =>
            if active then {
              val fetched = InteractionVariableHistory[F](InteractionEvents(received.getOrElse(key, Nil).toSet))
              val merged = variable.currentHistory.mergeWith(fetched).normalize()
              if merged != variable.currentHistory then variable.updateHistory(_ => merged, fullInfo.workbookContextInformation)
            }
          }
        }.map(_ => ())
      }
    }

    val report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
      cache.reports.toSignal(Map.empty).map(_.get(key).map(current =>
        InteractionVariableSyncReport(destinations.map(_.information), current.syncedDestinations.map(_.information))))
  }

  private val bindings: StrictSignal[Seq[Binding]] = fullInfo.signals.syncEnvironmentSignal.map { current =>
    resetting.set(SyncResetState.Idle)
    current.toSeq.flatMap(owner => owner.interactions.toSeq.map(interaction => new TypedBinding(interaction, owner)))
  }.observe(using unsafeWindowOwner)

  private def attempt[A](operation: => Future[A]): Future[A] = Try(operation).fold(Future.failed, identity)
  private def track(operation: Future[Unit]): Future[Unit] = {
    pending.update(_ + operation)
    operation.onComplete(_ => pending.update(_ - operation))
    operation
  }
  private def flush(owner: Option[WorkbookSyncEnvironment]): Unit =
    owner.foreach(_.syncDestinations.foreach(_.handler.flush(true)))

  /** Register the whole phase before flushing shared handlers once. */
  private def batch(selected: Seq[Binding], forceFlush: Boolean)(operation: Binding => Future[Unit]): Future[Unit] = {
    if !enabled then Future.unit else {
      val owner = environment
      val operations = selected.map(binding => attempt(operation(binding)))
      val flushing = if forceFlush then Try(flush(owner)).toEither else Right(())
      val completed = Future.sequence(operations.map(_.transform(result => scala.util.Success(result))))
        .flatMap(results => results.collectFirst { case scala.util.Failure(error) => error }
          .orElse(flushing.left.toOption).fold(Future.unit)(Future.failed))
      track(completed)
    }
  }

  override def storeAll(forceFlush: Boolean): Future[Unit] = batch(bindings.now(), forceFlush)(_.store())
  override def fetchAll(forceFlush: Boolean): Future[Unit] = batch(bindings.now(), forceFlush)(_.fetch())
  override def store[F <: InteractionFormat](variable: InteractionVariable[F], forceFlush: Boolean): Future[Unit] =
    batch(bindings.now().filter(_.variable eq variable), forceFlush)(_.store())

  override def ensureSynced(): Future[Unit] = resetting.now() match {
    case SyncResetState.Running(completion) => completion
    case SyncResetState.Idle => super.ensureSynced()
    case _ => Future.unit // A partial reset stays paused so mounted answers cannot recreate deleted rows.
  }

  def resetAllData(): Future[Unit] = resetting.now() match {
    case SyncResetState.Running(completion) => completion
    case _ =>
      val owner = environment
      val completion = Promise[Unit]()
      resetting.set(SyncResetState.Running(completion.future))
      fullInfo.invalidateFeedbackSession()
      owner.foreach(_.interactions.foreach(_.interactionVariable.resetForNewSession()))
      val draining = pending.now().toSeq.map(_.recover { case scala.util.control.NonFatal(_) => () })
      val reset = attempt {
        flush(owner)
        Future.sequence(draining).flatMap { _ =>
          val deletions = owner.toSeq.flatMap(_.syncDestinations.toSeq).map(destination =>
            attempt(destination.handler.clearAllHistories(destination.usageContext)).map(_ => ()))
          Future.sequence(deletions.map(_.transform(result => scala.util.Success(result)))).flatMap(results =>
            results.collectFirst { case scala.util.Failure(error) => error }.fold(Future.unit)(Future.failed))
        }
      }
      reset.onComplete { result =>
        if environment == owner then resetting.set(result.fold(SyncResetState.Failed(_), _ => SyncResetState.Completed))
        completion.tryComplete(result)
      }
      completion.future
  }

  def createReportFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
    bindings.flatMapSwitch(current => current.find(_.variable eq interaction.interactionVariable)
      .fold(Signal.fromValue(Option.empty[InteractionVariableSyncReport[SyncInformationWithContext]]))(_.report))
}
