package it.evadid.workbook.interaction.sync.destination

import it.evadid.workbook.interaction.sync.SyncSuccess

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationForKey[K, V] {
  def forKey: K

  def storeValue(value: V)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def readValue(implicit ec: ExecutionContext): Future[V]
}
