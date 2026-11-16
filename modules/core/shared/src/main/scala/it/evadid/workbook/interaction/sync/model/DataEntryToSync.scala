package it.evadid.workbook.interaction.sync.model

import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*


import java.time.LocalDateTime
import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

case class DataEntryToSync[K , D](
   dataKey: K,
   dataValue: D,
   direction: SyncDirection
) derives ReadWriter {

}
