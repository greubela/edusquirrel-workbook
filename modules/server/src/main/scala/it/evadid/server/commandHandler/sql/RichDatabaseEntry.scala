package it.evadid.server.commandHandler.sql

import it.evadid.workbook.model.interaction.sync.SyncFormatter.{RichInteractionVariableFormatter, RichInteractionVariableHistorySerialized}
import it.evadid.workbook.model.interaction.sync.{SyncContext, UsageContext}

sealed trait RichDatabaseEntry {
  def keyForSerialisation: String

  def eventId: String

  def richHistory: RichInteractionVariableHistorySerialized

  def usageContext: UsageContext

  def syncContext: SyncContext = usageContext.toSyncContext(keyForSerialisation)

  def toMapEntry: (SyncContext, RichDatabaseEntry) = usageContext.toSyncContext(keyForSerialisation) -> this

}

object RichDatabaseEntry {

  def withoutKey(usageContext: UsageContext, formatter: RichInteractionVariableFormatter, list: List[String]): RichDatabaseEntry = {
    DatabaseEntryWithoutKey(usageContext, list.head, formatter.deserializeAsRich(list(1)))
  }

  def withKey(usageContext: UsageContext, formatter: RichInteractionVariableFormatter, list: List[String]): RichDatabaseEntry = {
    DatabaseEntryWithKey(usageContext, list.head, list(1), formatter.deserializeAsRich(list(2)))
  }

  def apply(usageContext: UsageContext, formatter: RichInteractionVariableFormatter, list: List[String]) = {
    if (list.size == 2) withoutKey(usageContext, formatter, list)
    else if (list.size == 3) withKey(usageContext, formatter, list)
    else throw new IllegalArgumentException("list must have 2 or 3 elements")
  }

  private[sql] case class DatabaseEntryWithoutKey(usageContext: UsageContext, eventId: String, richHistory: RichInteractionVariableHistorySerialized) extends RichDatabaseEntry {
    lazy val keyForSerialisation: String = richHistory.keyForSerialisation

  }

  private[sql] case class DatabaseEntryWithKey(usageContext: UsageContext, eventId: String, keyForSerialisation: String, richHistory: RichInteractionVariableHistorySerialized) extends RichDatabaseEntry {
  }


}