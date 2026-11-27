package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SyncDestinationHandlerBasic {

  def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]]

  def storeBulk(elementMap: Map[String, String])(implicit ec: ExecutionContext): Future[SyncSuccess[String]] =
    Future.traverse(elementMap.toList)(storeElement).map(_.foldLeft(SyncSuccess.emptyNow[String]())(_.combine(_)))

  def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess[String]]

  def updateElement[R](key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R]

  def readElement(key: String)(implicit ec: ExecutionContext): Future[String]

  def readAll(implicit ec: ExecutionContext): Future[Map[String, String]]

  def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]]

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
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = {
      basicHandler.storeElementRaw(syncLogger, key, value).transform {
        case Success(syncResult) =>
          syncLogger.log(s"Successfully stored key->Value pair to ${toString}: ${key.take(maxCharLengthForLogging)}->${value.take(maxCharLengthForLogging)}", INFO, Some(true))
          Success(syncResult)
        case Failure(err) =>
          syncLogger.logExceptionWarn(s"Ignoring store (of key ${key.take(maxCharLengthForLogging)} because of error during storing", err)
          Failure(err)
      }(using ec)
    }

    def updateElement[R](key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] =
      scala.util.Try(basicHandler.updateElementRaw(syncLogger, key, update)).fold(Future.failed, identity)

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

    override def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = basicHandler.clearAll(syncLogger)

    override def underlyingDefinition: SyncDestinationDefinition = basicHandler.underlyingDefinition

    override def syncLogger: SyncLogger = pSyncLogger

    override def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = {
      basicHandler.removeKeyRaw(syncLogger, key)
    }
  }
}