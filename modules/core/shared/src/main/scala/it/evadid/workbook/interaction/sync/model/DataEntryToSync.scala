package it.evadid.workbook.interaction.sync.model

case class DataEntryToSync[K, D](
    dataKey: K,
    dataValue: D,
    direction: SyncDirection
)
