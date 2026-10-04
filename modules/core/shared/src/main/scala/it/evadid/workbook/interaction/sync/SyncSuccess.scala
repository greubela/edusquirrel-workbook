package it.evadid.workbook.interaction.sync

import java.time.LocalDateTime

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

case class SyncSuccess(elementsAdded: Int, elementsChanged: Int, elementsRemoved: Int, timestampCommitted: LocalDateTime) derives ReadWriter {
  def combine(other: SyncSuccess): SyncSuccess = {
    val later = if (timestampCommitted.isBefore(other.timestampCommitted)) other.timestampCommitted else timestampCommitted
    SyncSuccess(elementsAdded + other.elementsAdded, elementsChanged + other.elementsChanged, elementsRemoved + other.elementsRemoved, later)
  }
}

object SyncSuccess {
  def emptyNow(): SyncSuccess = SyncSuccess(0, 0, 0, LocalDateTime.now())
}
