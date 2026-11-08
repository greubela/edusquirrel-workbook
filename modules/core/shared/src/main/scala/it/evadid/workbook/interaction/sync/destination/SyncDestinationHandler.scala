package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*
trait SyncDestinationHandler {

  def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess]

  def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess]

  def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess]

  def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String]

  def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]]

  def underlyingDefinition: SyncDestinationDefinition

  def withSyncLogger(logger: SyncLogger): SyncDestinationHandlerBasic = SyncDestinationHandlerBasic(this, logger)

  def forHistory(logger: SyncLogger): SyncDestinationHistoryHandler = withSyncLogger(logger).forHistory()
}

object SyncDestinationHandler {


  /*
    trait SyncDestinationHandlerRaw extends SyncDestinationHandler {

      protected given ExecutionContext = ExecutionContext.global

      protected def readRaw(key: String): Future[String] = {
        readAllRaw().map(resMap => {
          val matching = resMap.toList.filter(_._1 == key)
          if (matching.isEmpty) throw SerializedException(s"${syncPrefix} Read was successfull, but element '${key}' not found!")
          else matching.head._2
        })
      }

      protected def readAllRaw(): Future[Map[String, String]]

      protected def storeToRaw(key: String, value: String): Future[Boolean]

      private def syncPrefix: String = "SyncDestination::" + this.getClass.getSimpleName

      def readRaw(logger: SyncLogger, key: String): Future[String] = {
        val resFut = readRaw(key)
        resFut.onComplete {
          case Success(res) => logger.log(s"${syncPrefix} successfully read key ${key}: ${res.take(60)}!", INFO, Some(false))
          case Failure(exception) => logger.log(s"${syncPrefix} failed to read key ${key}, returning nothing", WARN, Some(false))
        }
        resFut
      }

      def readAllRaw(logger: SyncLogger): Future[Map[String, String]] = {
        val resFut = readAllRaw()
        resFut.onComplete {
          case Success(res) => logger.log(s"${syncPrefix} successfully read ${res.knownSize} elements!", INFO, Some(false))
          case Failure(exception) => logger.logException(s"${syncPrefix} failed to read all elements returning empty map", exception, Some(false), WARN)
        }
        resFut
      }

      def storeToRaw(logger: SyncLogger, key: String, value: String): Future[Boolean] = {
        val resFut = storeToRaw(key, value)
        resFut.onComplete {
          case Success(res) => logger.log(s"SyncDestination successfully stored key ${key} (value ${value.take(60)})", INFO, Some(false))
          case Failure(exception) => logger.logException(s"${syncPrefix} failed to write key ${key} (value ${value.take(60)}), ignoring write", exception, Some(false), WARN)
        }
        resFut
      }

      def storeTo(logger: SyncLogger, key: String, value: String, keyFormatter: String => String, valueFormatter: String => String): Future[Boolean] = {
        val keyMapped = keyFormatter(key)
        val valMapped = valueFormatter(value)
        storeToRaw(logger, keyMapped, valMapped)
      }

      def storeWithValuePrefixed(logger: SyncLogger, key: String, value: String, prefix: String): Future[Boolean] = {
        storeTo(logger, key, value, pKey => prefix + pKey, pValue => pValue)
      }

      def readAllWithPrefix(logger: SyncLogger, key: String, value: String, prefix: String): Future[Map[String, String]] = {
        val mapAllFut = readAllRaw()

        mapAllFut.transform {
          case Success(mapAll) => {
            val mapPrefixed = mapAll.filter(_._2.startsWith(prefix))
            logger.log(s"${syncPrefix} successfully read ${mapPrefixed.knownSize} elements with prefix ${prefix} (${mapAll.knownSize} elements known in total)!", INFO, Some(false))
            Success(mapPrefixed)
          }
          case Failure(err) => {
            logger.logException(s"${syncPrefix} failed to read all with prefix ${prefix}, returning nothing", err, Some(false), WARN)
            Success(Map())
          }
        }

      }

    }*/


}

