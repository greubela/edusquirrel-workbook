package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.FetchResponse
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SyncDestinationHistoryHandler {

  def syncLogger: SyncLogger

  def definition: SyncDestinationDefinition

  def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def storeHistoriesJson(contextJsonMap: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    Future.traverse(contextJsonMap.toList)(storeHistoryJson).map(_.fold(SyncSuccess.emptyNow())(_.combine(_)))
  }

  def flush(forceFlush: Boolean): Unit = {}

  def readHistoryJson(syncContext: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    readHistoriesJson(syncContext.toUsageContext).map(resp => {
      val newSet = resp.fetchedValues.filter(_.dataKey.doesMatch(syncContext.toUsageContext)).filter(_.dataKey.keyForSerialisation == syncContext.keyForSerialisation)
      resp.copy(fetchedValues = newSet)
    })
  }

  def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]]

  def readHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](syncContext: SyncContext)(implicit ec: ExecutionContext): Future[InteractionVariableHistory[T, GA, SA]] = {
    readHistoryJson(syncContext).transformWith {
      case Success(curResponse) => Future.fromTry(SyncDestinationHistoryHandler.readHistoryFrom(Serializer.fromUpickleJson(InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA]), syncContext, curResponse).toTry)
      case Failure(err) => Future.failed(SerializedException(s"Error while fetching data from sync destination (${definition}): ${err.getMessage}", err))
    }
  }

  def readHistories(usageContext: UsageContext, variablesToResolveTypes: Seq[InteractionVariable[?, ?, ?]])(implicit ec: ExecutionContext): Future[Map[SyncContext, InteractionVariableHistory[?, ?, ?]]] = {
    readHistoriesJson(usageContext).map(fetchResponse => SyncDestinationHistoryHandler.readHistoriesFrom(variablesToResolveTypes, usageContext, fetchResponse))
  }

  lazy val toBuffered: BufferedSyncDestinationHandler = BufferedSyncDestinationHandler(this)

}


object SyncDestinationHistoryHandler {

  private[sync] def readHistoriesFrom(
                                       variablesToResolveTypes: Seq[InteractionVariable[?, ?, ?]],
                                       usageContext: UsageContext,
                                       fetchResponse: FetchResponse[SyncContext, String]
                                     ): Map[SyncContext, InteractionVariableHistory[?, ?, ?]] = {
    fetchResponse.fetchedValues.flatMap(curFetchedValue => {
      variablesToResolveTypes
        .find(_.keyForSerialization == curFetchedValue.dataKey.keyForSerialisation)
        .flatMap(curVar => (SyncDestinationHistoryHandler.readHistoryFrom(curVar.serializerHistory, curFetchedValue.dataKey, fetchResponse).toOption.map(curHist => curFetchedValue.dataKey -> curHist))).toMap
    }).toMap
  }

  private[sync] def readHistoryFrom[T, GA, SA](
                                                serializer: Serializer[InteractionVariableHistory[T, GA, SA]],
                                                syncContext: SyncContext,
                                                fetchResponse: FetchResponse[SyncContext, String]
                                              ): Either[SerializedException, InteractionVariableHistory[T, GA, SA]] = {
    fetchResponse.fetchedValues.find(_.dataKey.keyForSerialisation == syncContext.keyForSerialisation).map(_.dataValue).match {
      case Some(jsonHistory) => serializer.tryDeserialize(jsonHistory).match {
        case Left(err) => Left(SerializedException(s"readHistory::curResponse fetched ${fetchResponse.fetchedValues.size} and contained '${syncContext}' but value could not be parsed (${err.getMessage})", err))
        case Right(history) => Right(history)
      }
      case None => Left(SerializedException(s"readHistory::curResponse fetched ${fetchResponse.fetchedValues.size} but did not contain ${syncContext}"))
    }
  }

  private def historyTimestamp(json: String): Option[LocalDateTime] =
    scala.util.Try(it.evadid.workbook.interaction.sync.model.HistoryJson.lastTimestamp(json)).toOption.flatten

  def fromBasic(basic: SyncDestinationHandlerBasic): SyncDestinationHistoryHandler = {
    val typed = basic.forSpecificType[SyncContext, String]

    new SyncDestinationHistoryHandler {
      override def syncLogger: SyncLogger = basic.syncLogger

      override def definition: SyncDestinationDefinition = basic.underlyingDefinition

      override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = typed.clearValue(context)

      override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = readHistoriesJson(context).flatMap(response => {
        val containedKeys = response.fetchedValues.map(_.dataKey).filter(cur => cur.programId == context.programId && cur.scenarioId == context.scenarioId && cur.userId == context.userId)
        Future.traverse(containedKeys)(clearValue).map(_ => SyncSuccess(0, 0, containedKeys.size, LocalDateTime.now()))
      })

      override def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
        typed.storeElementTyped(syncContext, historyJson)
      }

      override def storeHistoriesJson(contextJsonMap: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
        typed.storeAll(contextJsonMap)
      }

      override def readHistoryJson(syncContext: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
        typed.readElementTyped(syncContext).map(curHistoryJson => {
          FetchResponse.fromMap(LocalDateTime.now(), Map(syncContext -> curHistoryJson), historyTimestamp)
        })
      }

      override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
        typed.readFullStore.map(curMap => {
          val data = curMap.filter((context, _) => context.toUsageContext == usageContext)
          FetchResponse.fromMap(LocalDateTime.now(), data, historyTimestamp)
        })
      }
    }
  }

}
