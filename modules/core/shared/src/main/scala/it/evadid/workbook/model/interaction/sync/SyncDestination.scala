package it.evadid.workbook.model.interaction.sync

import it.evadid.core.util.InfoUtil
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized
import upickle.implicits.key

trait SyncDestination {

  protected def debug: Boolean = false

  def syncTo(key: String, value: String): Unit

  def syncAllFrom(): Map[String, String]

  def syncKeyFrom(key: String): Option[String]

  def syncTo[T](interaction: WorkbookInteraction[T], keyForSerialization: String, serializedHistory: InteractionVariableHistorySerialized): Unit = {
    // own class: InteractionVariableSerializedHumanReadable (?)
    val lastState = interaction.interactionVariable.history.lastState
    val lastUpdate = InfoUtil.datetimeFormattedForDb(lastState.timestamp)
    val lastValue = interaction.serializer.serialize(lastState.value)

    val writeValue =
      s"""{
         |  "syncKey: ": "${keyForSerialization}" ,
         |  "lastUpdate": "${lastUpdate}",
         |  "lastValue": "${lastValue}",
         |  "fullHistory": "${serializedHistory.toString}"
         |}
         |""".stripMargin

    if (debug) println("[INFO] history '" + keyForSerialization + "' changed, synced to " + this.getClass.getSimpleName
      + ", current value: " + lastValue
      + ", last update time: " + lastUpdate)

    syncTo(keyForSerialization, writeValue)
  }

  /*
  def syncedUntil: Future[LocalDateTime]
   */

}

