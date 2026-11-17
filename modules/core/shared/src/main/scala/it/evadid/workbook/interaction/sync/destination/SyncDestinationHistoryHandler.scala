package it.evadid.workbook.interaction.sync.destination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
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

  def readHistory[F <: InteractionFormat](syncContext: SyncContext)(implicit ec: ExecutionContext): Future[InteractionVariableHistory[F]] = {
    // NOTE: This requires ValueOf[F] which we don't have, so we use the raw JSON and leave type conversion to caller
    readHistoryJson(syncContext).transformWith {
      case Success(curResponse) => 
        // For now, return an untyped history and let the caller handle type conversion
        Future.failed(SerializedException("readHistory requires ValueOf[F] for type-safe deserialization"))
      case Failure(err) => Future.failed(SerializedException(s"Error while fetching data from sync destination (${definition}): ${err.getMessage}", err))
    }
  }

  def readHistories(usageContext: UsageContext, variablesToResolveTypes: Seq[InteractionVariable[_ <: InteractionFormat]])(implicit ec: ExecutionContext): Future[Map[SyncContext, InteractionVariableHistory[_]]] = {
    readHistoriesJson(usageContext).map(fetchResponse => SyncDestinationHistoryHandler.readHistoriesFromTyped(variablesToResolveTypes, fetchResponse))
  }

  lazy val toBuffered: BufferedSyncDestinationHandler = {
    BufferedSyncDestinationHandler(this)
  }

}


object SyncDestinationHistoryHandler {

  private[sync] def readHistoriesFromTyped(
                                       variablesToResolveTypes: Seq[InteractionVariable[_ <: InteractionFormat]],
                                       fetchResponse: FetchResponse[SyncContext, String]
                                     ): Map[SyncContext, InteractionVariableHistory.InteractionVariableHistory3[_ <: InteractionFormat]] = {
    fetchResponse.fetchedValues.flatMap { curFetchedValue =>
      variablesToResolveTypes
        .find(_.keyForSerialization == curFetchedValue.dataKey.keyForSerialisation)
        .flatMap { curVar =>
          readHistoryFromTypedInternal(curVar, curFetchedValue.dataValue).toOption.map(curHist => curFetchedValue.dataKey -> curHist)
        }
    }.toMap
  }

  private[sync] def readHistoryFromTypedInternal(variable: InteractionVariable[_ <: InteractionFormat], jsonHistory: String): Either[SerializedException, InteractionVariableHistory.InteractionVariableHistory3[_ <: InteractionFormat]] = {
    // Since we're working with existential types, we deserialize using the format's serializer
    // and cast to the existential type
    val format = variable.underlyingInteraction.format
    format.historySerializer.tryDeserialize(jsonHistory) match {
      case Left(err) => Left(SerializedException(s"readHistoryFromTypedInternal: value could not be parsed (${err.getMessage})", err))
      case Right(history) => Right(history.asInstanceOf[InteractionVariableHistory.InteractionVariableHistory3[_ <: InteractionFormat]])
    }
  }

  private[sync] def readHistoryFromTypedConcrete[F <: InteractionFormat](variable: InteractionVariable[F], jsonHistory: String)(using v: ValueOf[F]): Either[SerializedException, InteractionVariableHistory[F]] = {
    val serializer = v.value.historySerializer
    serializer.tryDeserialize(jsonHistory) match {
      case Left(err) => Left(SerializedException(s"readHistoryFromTypedConcrete: value could not be parsed (${err.getMessage})", err))
      case Right(history) => Right(history.asInstanceOf[InteractionVariableHistory[F]])
    }
  }

  private[sync] def readHistoryFrom[F <: InteractionFormat](
                                                                syncContext: SyncContext,
                                                                fetchResponse: FetchResponse[SyncContext, String]
                                                              )(using v: ValueOf[F]): Either[SerializedException, InteractionVariableHistory[F]] = {
    val serializer = v.value.historySerializer
    fetchResponse.fetchedValues.find(_.dataKey == syncContext).map(_.dataValue) match {
      case Some(jsonHistory) => 
        serializer.tryDeserialize(jsonHistory) match {
          case Left(err) => Left(SerializedException(s"readHistory::curResponse fetched ${fetchResponse.fetchedValues.size} and contained '${syncContext}' but value could not be parsed (${err.getMessage})", err))
          case Right(history) => Right(history.asInstanceOf[InteractionVariableHistory[F]])
        }
      case None => Left(SerializedException(s"readHistory::curResponse fetched ${fetchResponse.fetchedValues.size} but did not contain ${syncContext}"))
    }
  }

  private def validHistory(json: String, logger: SyncLogger): Boolean = ???

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
          FetchResponse.fromMap(LocalDateTime.now(), Map(syncContext -> curHistoryJson).filter((_, json) => validHistory(json, syncLogger)))
        })
      }

      override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
        typed.readFullStore.map(curMap => {
          val data = curMap.filter((context, json) => context.toUsageContext == usageContext && validHistory(json, syncLogger))
          FetchResponse.fromMap(LocalDateTime.now(), data)
        })
      }
    }
  }

}
