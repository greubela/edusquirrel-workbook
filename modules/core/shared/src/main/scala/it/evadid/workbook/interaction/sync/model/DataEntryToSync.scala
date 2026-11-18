package it.evadid.workbook.interaction.sync.model

import upickle.default.ReadWriter

case class DataEntryToSync[K, D](
    dataKey: K,
    dataValue: D,
    direction: SyncDirection
) derives ReadWriter
