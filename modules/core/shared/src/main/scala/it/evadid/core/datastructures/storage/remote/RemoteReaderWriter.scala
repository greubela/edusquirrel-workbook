package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.storage.remote.RemoteReaderWriter.{RemoteDataReader, RemoteDataWriter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.sync.model.FetchResponse

import scala.concurrent.{ExecutionContext, Future}

trait RemoteReaderWriter[K, D] {
  def reader: RemoteDataReader[K, D]

  def writer: RemoteDataWriter[K, D]
}

object RemoteReaderWriter {

  private given ec: ExecutionContext = ExecutionContext.global

  trait RemoteDataReader[K, D] {
    def fetchByKey(logger: SyncLogger, key: K): Future[FetchResponse[K, D]]

    def fetchByKeys(logger: SyncLogger, keys: Set[K]): Future[FetchResponse[K, D]] = Future.traverse(keys)(curKey => fetchByKey(logger, curKey)).map(responses => responses.fold(responses.head)(_.combineWith(_)))

    def fetchAll(logger: SyncLogger): Future[FetchResponse[K, D]]

    def flush(logger: SyncLogger, forceFlush: Boolean): Unit
  }

  trait RemoteDataWriter[K, D] {
    def writeForKey(logger: SyncLogger, key: K, dataValue: D): Future[SyncSuccess]

    def writeAll(logger: SyncLogger, map: Map[K, D]): Future[SyncSuccess]

    def flush(logger: SyncLogger, forceFlush: Boolean): Unit
  }
  /*
    trait RemoteCacheConfig[K, D] {
      def reader: RemoteDataReader[K, D]

      def writer: RemoteDataWriter[K, D]

      def syncLogger: SyncLogger

      def flush(forceFlush: Boolean)(using ec: ExecutionContext): Future[Unit] = Future {
        reader.flush(syncLogger, forceFlush)
        writer.flush(syncLogger, forceFlush)
      }

    }*/

}