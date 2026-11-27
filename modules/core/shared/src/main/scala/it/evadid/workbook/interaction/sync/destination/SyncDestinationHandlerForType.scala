package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.command.SerializedException
import it.evadid.util.MapUtils
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.workbook.interaction.sync.SyncSuccess

import scala.concurrent.{ExecutionContext, Future}

/** Typed key-value access; results name the affected typed keys. */
trait SyncDestinationHandlerForType[K, V] {

  def clearValue(key: K)(implicit ec: ExecutionContext): Future[SyncSuccess[K]]

  def storeElementTyped(key: K, value: V)(implicit ec: ExecutionContext): Future[SyncSuccess[K]]

  def readElementTyped(key: K)(implicit ec: ExecutionContext): Future[V]

  def readFullStore(implicit ec: ExecutionContext): Future[Map[K, V]]

  def storeAll(valueMap: Map[K, V])(implicit ec: ExecutionContext): Future[SyncSuccess[K]]

  def forSpecificKey(key: K): SyncDestinationForKey[K, V] = new SyncDestinationForKey[K, V]() {
    def forKey: K = key

    def storeValue(value: V)(implicit ec: ExecutionContext): Future[SyncSuccess[K]] = storeElementTyped(forKey, value)

    def readValue(implicit ec: ExecutionContext): Future[V] = readElementTyped(forKey)

    def clear(implicit ec: ExecutionContext): Future[SyncSuccess[K]] = clearValue(forKey)
  }
}

object SyncDestinationHandlerForType {

  def fromBasic[K, V](basic: SyncDestinationHandlerBasic, keySerializer: Serializer[K], valueSerializer: Serializer[V]): SyncDestinationHandlerForType[K, V] = new SyncDestinationHandlerForType[K, V] {


    override def storeElementTyped(key: K, value: V)(implicit ec: ExecutionContext): Future[SyncSuccess[K]] = {
      val tupConverter: TypeConverter[(K, V), (String, String)] = TypeConverter.tupleConverter(keySerializer, valueSerializer)
      tupConverter.tryConvertToO((key, value)).match {
        case Left(err) => Future.failed(err)
        case Right((keyStr, valStr)) => basic.storeElement(keyStr, valStr).map(_.map(_ => key))
      }
    }

    override def readElementTyped(key: K)(implicit ec: ExecutionContext): Future[V] = {
      keySerializer.trySerialize(key).match {
        case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not read element because key serialisation failed: ${err.getLocalizedMessage}", err))
        case Right(key) => basic.readElement(key)(using ec).flatMap(valueStr => {
          valueSerializer.tryDeserialize(valueStr).match {
            case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not deserialize successfully read value (for key '${key.take(60)}'): ${err.getMessage}'", err))
            case Right(value) => Future.successful(value)
          }
        })
      }
    }

    override def clearValue(key: K)(implicit ec: ExecutionContext): Future[SyncSuccess[K]] = {
      keySerializer.trySerialize(key).match {
        case Left(err) => Future.failed(SerializedException(s"${basic.underlyingDefinition}:Could not remove element because key serialisation failed: ${err.getLocalizedMessage}", err))
        case Right(serialized) => basic.clearValue(serialized)(using ec).map(_.map(_ => key))
      }
    }

    private def deserializeAndWarn(input: Map[String, String]): Map[K, V] = {
      val (converted, rejected) = MapUtils.convertMap(input)(keySerializer.reversed, valueSerializer.reversed)
      if (rejected.nonEmpty) basic.syncLogger.log(
        s"${basic.underlyingDefinition}: Ignoring ${rejected.size} unreadable records out of ${input.size}; saved data must match the current schema.", WARN, Some(false))
      converted
    }

    private def serializeAndWarn(input: Map[K, V]): Map[String, String] = {
      val (converted, rejected) = MapUtils.convertMap(input)(keySerializer, valueSerializer)
      if (rejected.nonEmpty) basic.syncLogger.log(
        s"${basic.underlyingDefinition}: Could not serialize ${rejected.size} records out of ${input.size}; excluding them from this store request.", WARN, Some(true))
      converted
    }

    override def storeAll(valueMap: Map[K, V])(implicit ec: ExecutionContext): Future[SyncSuccess[K]] = {
      if (valueMap.isEmpty) Future.successful(SyncSuccess.emptyNow[K]()) else {
        val res: Map[String, String] = serializeAndWarn(valueMap)
        if (res.isEmpty) Future.failed(SerializedException("Could not serialize a single element, ignoring store request!"))
        else {
          val keys = valueMap.keys.flatMap(key => keySerializer.trySerialize(key).toOption.map(_ -> key)).toMap
          basic.storeBulk(res).map(_.map(keys))
        }
      }
    }

    override def readFullStore(implicit ec: ExecutionContext): Future[Map[K, V]] = basic.readAll.map(deserializeAndWarn)

  }
}
