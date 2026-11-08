package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SyncDestinationHandlerBasic {

  def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def storeBulk(elementMap: Map[String, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    Future.traverse(elementMap.toList)(storeElement).map(syncSuccesses => SyncSuccess(syncSuccesses.map(_.elementsAdded).sum, 0, 0, LocalDateTime.now()))
  }

  def readElement(key: String)(implicit ec: ExecutionContext): Future[String]

  def readAll(implicit ec: ExecutionContext): Future[Map[String, String]]

  def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def underlyingDefinition: SyncDestinationDefinition

  def syncLogger: SyncLogger

  // Typed
  def forSpecificType[K, V](implicit keyRw: ReadWriter[K], valueRw: ReadWriter[V]): SyncDestinationHandlerForType[K, V] = {
    forSpecificType[K, V](Serializer.fromUpickleJson(keyRw), Serializer.fromUpickleJson(valueRw))
  }

  def forSpecificType[K, V](keySerializer: Serializer[K], valueSerializer: Serializer[V]): SyncDestinationHandlerForType[K, V] = SyncDestinationHandlerForType.fromBasic(this, keySerializer, valueSerializer)




  // History

  def forHistory(): SyncDestinationHistoryHandler = SyncDestinationHistoryHandler.fromBasic(this)


}

object SyncDestinationHandlerBasic {

  def apply(basicHandler: SyncDestinationHandler, pSyncLogger: SyncLogger): SyncDestinationHandlerBasic = new SyncDestinationHandlerBasic() {
    private val maxCharLengthForLogging: Int = 60

    // Basic
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      basicHandler.storeElementRaw(syncLogger, key, value).transform {
        case Success(syncResult) =>
          syncLogger.log(s"Successfully stored key->Value pair to ${toString}: ${key.take(maxCharLengthForLogging)}->${value.take(maxCharLengthForLogging)}", INFO, Some(true))
          Success(syncResult)
        case Failure(err) =>
          syncLogger.logExceptionWarn(s"Ignoring store (of key ${key.take(maxCharLengthForLogging)} because of error during storing", err)
          Failure(err)
      }(using ec)
    }

    def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = {
      basicHandler.readElementRaw(syncLogger, key).transform {
        case Success(result) =>
          syncLogger.log(s"Successfully read key (${key.take(maxCharLengthForLogging)}): ${result.take(maxCharLengthForLogging)}", INFO, Some(false))
          Success(result)
        case Failure(err) =>
          syncLogger.logExceptionWarn(s"Ignoring store (of key ${key.take(maxCharLengthForLogging)} because of error during storing", err)
          Failure(err)
      }(using ec)
    }

    def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = basicHandler.readDestinationRaw(syncLogger)(using ec)

    // Raw
    def withReplacedSyncLogger(newLogger: SyncLogger): SyncDestinationHandlerBasic = SyncDestinationHandlerBasic(basicHandler, newLogger)

    override def underlyingDefinition: SyncDestinationDefinition = basicHandler.underlyingDefinition

    override def syncLogger: SyncLogger = pSyncLogger

    override def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      basicHandler.removeKeyRaw(syncLogger, key)
    }
  }
}