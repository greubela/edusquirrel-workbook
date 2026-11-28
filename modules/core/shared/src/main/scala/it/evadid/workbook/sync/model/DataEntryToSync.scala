package it.evadid.workbook.sync.model

case class DataEntryToSync[K, D](
    dataKey: K,
    dataValue: D,
    direction: SyncDirection
)
