package it.evadid.workbook.support

import it.evadid.workbook.model.InteractionInfo

import it.evadid.workbook.model.InteractionScope

import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.control.InteractionController
import it.evadid.workbook.sync.*
import it.evadid.workbook.sync.destination.{SyncDestinationDefinition, SyncDestinationHandlerBasic}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.model.history.InteractionVariableHistory
import it.evadid.workbook.abstractions.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future, Promise}

/** An in-memory history destination whose operations wait for `gate` while it is set, standing in for a device or
  * server that holds histories of `context`.
  */
final class GatedHistoryStore(val context: UsageContext) extends SyncDestinationHandlerBasic {
  private given ExecutionContext = ExecutionContext.global
  val values = State(Map.empty[String, String])
  val gate = State(Option.empty[Promise[Unit]])
  val syncLogger: SyncLogger = SyncLogger(BasicLogger())
  val underlyingDefinition = SyncDestinationDefinition.defaultBrowserHistory

  private def gated[A](operation: => A): Future[A] = gate.now().fold(Future.unit)(_.future).map(_ => synchronized(operation))
  def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = gated(values.now())
  def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = gated(values.now()(key))
  def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] =
    updateElement(key, previous => value -> SyncSuccess.written(key, previous, value))
  def updateElement[R](key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] = gated {
    val (value, result) = update(values.now().get(key))
    values.update(_.updated(key, value))
    result
  }
  def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = gated {
    values.update(_ - key)
    SyncSuccess.removed(Set(key))
  }
  def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = gated {
    val removed = values.now().keySet
    values.set(Map.empty)
    SyncSuccess.removed(removed)
  }

  /** Replaces the stored history of `interaction`, as another device or the server would have written it. */
  def seed[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], history: InteractionVariableHistory[F]): Unit =
    values.update(_.updated(SyncContext.serializer.serialize(context.toSyncContext(interaction.keyForSerialization)),
      InteractionHistoryJson.fromHistory(interaction.interactionFormat, history).serialized))

  /** A connection that sends every event to this store. */
  def connection(execution: ExecutionContextInformation = ExecutionContextInformation.default): SyncConnection = {
    val information = SyncInformation(SyncDestinationDefinition.defaultBrowserHistory, EventSyncStrategy.SYNC_EVERYTHING)
    SyncConnection(Set(information.forContext(context, _ => forHistory())), execution, syncLogger)
  }

  /** A controller with `interactions` bound to this store. */
  def controllerFor(interactions: WorkbookInteractionElement[? <: InteractionFormat]*): InteractionController = {
    val controller = InteractionController(State(InteractionInfo.empty(context)))
    controller.bind(InteractionScope(context, interactions, connection()))
    controller
  }
}
