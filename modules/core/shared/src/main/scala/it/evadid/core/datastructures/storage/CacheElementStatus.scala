package it.evadid.core.datastructures.storage

import it.evadid.distribution.command.SerializedException

sealed trait CacheElementStatus

object CacheElementStatus {
  case class PRESENT() extends CacheElementStatus

  case class DELETED() extends CacheElementStatus

  case class STARTED() extends CacheElementStatus

  case class FAILED(err: SerializedException) extends CacheElementStatus

  case class UNKNOWN() extends CacheElementStatus
}
