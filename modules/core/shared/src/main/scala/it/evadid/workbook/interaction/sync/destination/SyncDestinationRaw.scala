package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.LoggingLevel.{INFO, WARN}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.SyncDestination.SyncDestinationForType

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

import upickle.default.*

trait SyncDestinationRaw extends SyncDestination {


  def getSyncDestinationForType[T](logger: SyncLogger, usingKey: String)(implicit rw: ReadWriter[T]): SyncDestinationForType[T] = {
    getSyncDestinationForType[T](logger, usingKey, Serializer.fromUpickleJson(rw))
  }

  def getSyncDestinationForType[T](logger: SyncLogger, usingKey: String, serializer: Serializer[T]): SyncDestinationForType[T] = new SyncDestinationForType[T] {

    override def storeElement(obj: T): Future[Boolean] = {
      val serialized = serializer.serialize(obj)
      println(s"UGLY SYNCDESTINATION RAW. serialized:\n${serialized}")
      storeToRaw(logger, usingKey, serializer.serialize(obj))
    }

    override def readElement: Future[T] = {
      readRaw(logger, usingKey).map(strRes => try {
        serializer.deserialize(strRes)
      } catch case (err: Throwable) => {
        throw SerializedException(s"${SyncDestinationRaw.this.syncPrefix} read key '${usingKey}' but could not parse (${strRes.take(60)}): >>${err.getMessage}<<. full object:\n ${strRes}")
      })
    }
  }

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
      case Failure(exception) => logger.logException(s"${syncPrefix} failed to read key ${key}, returning nothing", exception, Some(false), WARN)
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

}










