package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.remote.RemoteCacheState
import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.RemoteValue
import it.evadid.workbook.interaction.sync.model.FetchResponse
import munit.FunSuite
import java.time.LocalDateTime

class RemoteStoragePackageSpec extends FunSuite {
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
  test("late observations cannot replace newer confirmed values") {
    val empty = RemoteCacheState.unknownState[String, String]
    val recent = RemoteValue[String, String]("recent", time)
    val current = empty.newInformationReceived("answer", recent)
    val delayed = current.newInformationReceived("answer", RemoteValue[String, String]("old", time.minusSeconds(1)))
    assertEquals(delayed.remoteValues("answer"), recent)
    assertEquals(empty.remoteValues, Map.empty)
    assertEquals(delayed.newestKnownValue, Some(("answer", "recent", time)))
  }
  test("fetch responses keep opaque data and expose the observation timestamp") {
    val response = FetchResponse.fromMap(time, Map("a" -> "unparsed JSON"))
    assertEquals(response.asMap("a"), RemoteValue[String, String]("unparsed JSON", time))
    val combined = response.combineWith(FetchResponse.fromMap(time.plusSeconds(1), Map("b" -> "other")))
    assertEquals(combined.asMap.keySet, Set("a", "b"))
    assertEquals(combined.timestampFetchResponse, time.plusSeconds(1))
  }
}
