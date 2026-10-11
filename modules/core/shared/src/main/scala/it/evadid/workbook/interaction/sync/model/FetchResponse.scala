package it.evadid.workbook.interaction.sync.model

import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*
import it.evadid.workbook.interaction.sync.model.SyncDirection.SERVER_TO_CLIENT
import upickle.default.*

import java.time.LocalDateTime
import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class FetchResponse[K, D]
(
  timestampFetchResponse: LocalDateTime,
  fetchedValues: Set[DataEntryToSync[K, D]]
) derives ReadWriter {

  protected def fetchedValuesFromMap(resMap: Map[K, D]): Set[DataEntryToSync[K, D]] = fetchedValuesFromIterator(resMap.iterator)

  protected def fetchedValuesFromIterator(resIt: Iterator[(K, D)]): Set[DataEntryToSync[K, D]] = {
    resIt.map(curTup => {
      DataEntryToSync(curTup._1, curTup._2, timestampFetchResponse, SERVER_TO_CLIENT)
    }).toSet
  }
}

object FetchResponse {

  def fromMap[K: ReadWriter, D: ReadWriter](timestamp: LocalDateTime, map: Map[K, D], readTime: D => Option[LocalDateTime]): FetchResponse[K, D] = {
    val res: Set[DataEntryToSync[K, D]] = {
      map.iterator.flatMap(tup => {
        val time = readTime(tup._2)
        if (time.isEmpty) None
        else Some(DataEntryToSync[K, D](tup._1, tup._2, time.get, SERVER_TO_CLIENT))
      }).toSet
    }
    new FetchResponse[K, D](timestamp, res)
  }
}
