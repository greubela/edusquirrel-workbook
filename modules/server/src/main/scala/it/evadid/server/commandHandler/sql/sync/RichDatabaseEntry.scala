package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}

sealed trait RichDatabaseEntry {
  def keyForSerialisation: String

  def eventId: String

  def historyJson: String

  def usageContext: UsageContext

  def syncContext: SyncContext = usageContext.toSyncContext(keyForSerialisation)

  def toMapEntry: (SyncContext, RichDatabaseEntry) = usageContext.toSyncContext(keyForSerialisation) -> this

}

object RichDatabaseEntry {

  def withoutKey(usageContext: UsageContext, list: List[String]): RichDatabaseEntry = {
    DatabaseEntryWithoutKey(usageContext, list.head, list(1))
  }

  def withKey(usageContext: UsageContext, list: List[String]): RichDatabaseEntry = {
    DatabaseEntryWithKey(usageContext, list.head, list(1), list(2))
  }

  def apply(usageContext: UsageContext, list: List[String]) = {
    if (list.size == 2) withoutKey(usageContext, list)
    else if (list.size == 3) withKey(usageContext, list)
    else throw new IllegalArgumentException("list must have 2 or 3 elements")
  }

  private[sql] case class DatabaseEntryWithoutKey(usageContext: UsageContext, eventId: String, historyJson: String) extends RichDatabaseEntry {
    lazy val keyForSerialisation: String = ujson.read(historyJson)("keyForSerialisation").str

  }

  private[sql] case class DatabaseEntryWithKey(usageContext: UsageContext, eventId: String, keyForSerialisation: String, historyJson: String) extends RichDatabaseEntry {
  }


}