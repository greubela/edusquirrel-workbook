package it.evadid.core.datastructures.storage

import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait CacheElementStatus derives ReadWriter

object CacheElementStatus {
  case class PRESENT() extends CacheElementStatus derives ReadWriter

  case class DELETED() extends CacheElementStatus derives ReadWriter

  case class STARTED() extends CacheElementStatus derives ReadWriter

  case class FAILED(err: SerializedException) extends CacheElementStatus derives ReadWriter

  case class UNKNOWN() extends CacheElementStatus derives ReadWriter
}
