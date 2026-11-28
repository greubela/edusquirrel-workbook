package it.evadid.workbook.sync.destination

import it.evadid.workbook.sync.SyncSuccess

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationForKey[K, V] {
  def forKey: K

  def storeValue(value: V)(implicit ec: ExecutionContext): Future[SyncSuccess[K]]

  def readValue(implicit ec: ExecutionContext): Future[V]
}
