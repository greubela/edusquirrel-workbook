package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.{RemoteHistoryCache, WorkbookSyncEnvironment}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.interaction.variable.events.InteractionEvents
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.meta.InteractionVariableSyncReport
import scala.concurrent.{ExecutionContext, Future}

/** A typed cache per interaction; the current environment owns all bindings and async guards. */
case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {
  private given ExecutionContext = ExecutionContext.global
  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private trait Binding {
    def variable: InteractionVariable[? <: InteractionFormat]
    def store(forceFlush: Boolean): Future[Unit]
    def fetch(forceFlush: Boolean): Future[Unit]
    def report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]]
  }

  private class TypedBinding[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], environment: WorkbookSyncEnvironment) extends Binding {
    val variable: InteractionVariable[F] = interaction.interactionVariable
    private val key = environment.usageContext.toSyncContext(variable.keyForSerialization)
    private val destinations = environment.syncDestinations.map(information => FormattedSyncDestination(information, interaction.interactionFormat))
    private val cache = RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]](syncLogger,
      destinations.map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap)
    private def active: Boolean = fullInfo.signals.syncEnvironmentSignal.now().contains(environment)

    def store(forceFlush: Boolean): Future[Unit] =
      if !active then Future.unit else cache.requestStoreToAll(Map(key -> variable.currentHistory), forceFlush)

    def fetch(forceFlush: Boolean): Future[Unit] = {
      cache.mergeLocal(Map(key -> variable.currentHistory))
      cache.fetchAll(Set(key), forceFlush).map { received =>
        if active then {
          val merged = variable.currentHistory.mergeWith(InteractionVariableHistory(InteractionEvents(received.getOrElse(key, Nil).toSet)))
          if merged != variable.currentHistory then variable.updateHistory(_ => merged, fullInfo.workbookContextInformation)
        }
      }
    }

    val report: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
      cache.reports.toSignal(Map.empty).map(_.get(key).map(current =>
        InteractionVariableSyncReport(destinations.map(_.information), current.syncedDestinations.map(_.information))))
  }

  private val bindings: StrictSignal[List[Binding]] = fullInfo.signals.syncEnvironmentSignal.map { environment =>
    environment.toList.flatMap(current => current.interactions.toList.map(interaction => new TypedBinding(interaction, current)))
  }.observe(using unsafeWindowOwner)

  override def storeAll(forceFlush: Boolean): Future[Unit] =
    Future.traverse(bindings.now())(_.store(forceFlush)).map(_ => ())
  override def fetchAll(forceFlush: Boolean): Future[Unit] =
    Future.traverse(bindings.now())(_.fetch(forceFlush)).map(_ => ())
  override def store[F <: InteractionFormat](variable: InteractionVariable[F], forceFlush: Boolean): Future[Unit] =
    bindings.now().find(_.variable eq variable).fold(Future.unit)(_.store(forceFlush))

  def createReportFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
    bindings.flatMapSwitch(current => current.find(_.variable eq interaction.interactionVariable)
      .fold(Signal.fromValue(Option.empty[InteractionVariableSyncReport[SyncInformationWithContext]]))(_.report))
}
