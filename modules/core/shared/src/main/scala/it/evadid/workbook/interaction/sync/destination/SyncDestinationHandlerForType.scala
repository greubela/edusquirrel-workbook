package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.command.SerializedException
import it.evadid.util.MapUtils
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.workbook.interaction.sync.SyncSuccess

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationHandlerForType[K, V] {

  def clearValue(key: K)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def storeElementTyped(key: K, value: V)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def readElementTyped(key: K)(implicit ec: ExecutionContext): Future[V]

  def readFullStore(implicit ec: ExecutionContext): Future[Map[K, V]]

  def storeAll(valueMap: Map[K, V])(implicit ec: ExecutionContext): Future[SyncSuccess]

  def forSpecificKey(key: K): SyncDestinationForKey[K, V] = new SyncDestinationForKey[K, V]() {
    def forKey: K = key

    def storeValue(value: V)(implicit ec: ExecutionContext): Future[SyncSuccess] = storeElementTyped(forKey, value)

    def readValue(implicit ec: ExecutionContext): Future[V] = readElementTyped(forKey)

    def clear(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValue(forKey)
  }
}

object SyncDestinationHandlerForType {

  def fromBasic[K, V](basic: SyncDestinationHandlerBasic, keySerializer: Serializer[K], valueSerializer: Serializer[V]): SyncDestinationHandlerForType[K, V] = new SyncDestinationHandlerForType[K, V] {


    override def storeElementTyped(key: K, value: V)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      val tupConverter: TypeConverter[(K, V), (String, String)] = TypeConverter.tupleConverter(keySerializer, valueSerializer)
      tupConverter.tryConvertToO((key, value)).match {
        case Left(err) => Future.failed(err)
        case Right((keyStr, valStr)) => basic.storeElement(keyStr, valStr)
      }
    }

    override def readElementTyped(key: K)(implicit ec: ExecutionContext): Future[V] = {
      keySerializer.trySerialize(key).match {
        case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not read element because key serialisation failed: ${err.getLocalizedMessage}", err))
        case Right(key) => basic.readElement(key)(ec).flatMap(valueStr => {
          valueSerializer.tryDeserialize(valueStr).match {
            case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not deserialize successfully read value (for key '${key.take(60)}'): ${err.getMessage}'", err))
            case Right(value) => Future.successful(value)
          }
        })
      }
    }

    override def clearValue(key: K)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      keySerializer.trySerialize(key).match {
        case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not remove element because key serialisation failed: ${err.getLocalizedMessage}", err))
        case Right(key) => basic.clearValue(key)(ec)
      }
    }

    private def deserializeAndWarn(input: Map[String, String]): Map[K, V] = {
      val res = MapUtils.convertMap(input)(keySerializer.reversed, valueSerializer.reversed)
      if (res.size < input.size) basic.syncLogger.log(s"${basic.underlyingDefinition}: Could not convert ${res._2.size} elements: ${res._2.toString}", WARN, Some(false))
      res._1
    }

    private def serializeAndWarn(input: Map[K, V]): Map[String, String] = {
      val res = MapUtils.convertMap(input)(keySerializer, valueSerializer)
      if (res.size < input.size) basic.syncLogger.log(s"${basic.underlyingDefinition}: Could not serialize ${res._2.size} elements, ignore them during storing: ${res._2.toString}", WARN, Some(true))
      res._1
    }

    override def storeAll(valueMap: Map[K, V])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      if (valueMap.isEmpty) Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now())) else {
        val res: Map[String, String] = serializeAndWarn(valueMap)
        if (res.isEmpty) Future.failed(SerializedException("Could not serialize a single element, ignoring store request!"))
        else basic.storeBulk(res)
      }
    }

    override def readFullStore(implicit ec: ExecutionContext): Future[Map[K, V]] = basic.readAll.map(deserializeAndWarn)

  }
}
