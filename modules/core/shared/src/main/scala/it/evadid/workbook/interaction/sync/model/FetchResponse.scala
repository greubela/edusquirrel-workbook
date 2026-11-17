package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.interaction.sync.model.SyncDirection.SERVER_TO_CLIENT
import upickle.default.*

import java.time.LocalDateTime

case class FetchResponse[K, D]
(
  timestampFetchResponse: LocalDateTime,
  fetchedValues: Set[DataEntryToSync[K, D]]
) derives ReadWriter {

  protected def fetchedValuesFromMap(resMap: Map[K, D]): Set[DataEntryToSync[K, D]] = fetchedValuesFromIterator(resMap.iterator)

  protected def fetchedValuesFromIterator(resIt: Iterator[(K, D)]): Set[DataEntryToSync[K, D]] = {
    resIt.map(curTup => {
      DataEntryToSync[K, D](curTup._1, curTup._2, SERVER_TO_CLIENT)
    }).toSet
  }

  def combineWith(other: FetchResponse[K, D]): FetchResponse[K, D] = FetchResponse(
    List(timestampFetchResponse, other.timestampFetchResponse).max,
    fetchedValues ++ other.fetchedValues
  )

  def asMap: Map[K, D] = fetchedValues.map(cur => cur.dataKey -> cur.dataValue).toMap

}

object FetchResponse {


  def fromMap[K, D](timestamp: LocalDateTime, map: Map[K, D]): FetchResponse[K, D] = {
    FetchResponse[K, D](timestamp, map.toSet.map( (curKey, curVal) => DataEntryToSync[K, D](curKey, curVal, SERVER_TO_CLIENT)))
  }
}
