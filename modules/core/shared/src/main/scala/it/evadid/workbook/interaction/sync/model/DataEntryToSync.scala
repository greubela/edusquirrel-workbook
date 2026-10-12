package it.evadid.workbook.interaction.sync.model

import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*


import java.time.LocalDateTime
import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

case class DataEntryToSync[K , D](
   dataKey: K,
   dataValue: D,
   timestamp: LocalDateTime, // timestamp of data created / last changed
   direction: SyncDirection
) derives ReadWriter {

}
