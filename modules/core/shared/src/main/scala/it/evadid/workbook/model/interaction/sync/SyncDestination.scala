package it.evadid.workbook.model.interaction.sync

import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized.RichInteractionVariableHistorySerialized

import java.time.LocalDateTime
import scala.concurrent.Future

trait SyncDestination {

  /*

abstract class SyncDestination[T](val io: Serializer[T]) {

  def resetStatus(): Unit = {
    syncState.set(ClientServerSyncStatus(SyncStatusUnsynced, SyncStatusUnsynced))
  }

}

  protected def trySyncTo(key: String, value: String): Future[Unit]

  protected def trySyncFrom(key: String): Future[RichInteractionVariableHistorySerialized]

   */

  protected def debug: Boolean = false

  def syncTo(key: String, value: String): Unit

  def syncAllFrom(): Map[String, String]

  def syncKeyFrom(key: String): Option[String]

  /*

    private val syncState = State[ClientServerSyncStatus](ClientServerSyncStatus(SyncStatusUnsynced, SyncStatusUnsynced))

  def ensureSynced(): Unit = {

  }

  def onElementsReceived(Set[String])

  */

  /*
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

  def syncedUntil: Future[LocalDateTime]
   */

}

object SyncDestination {

  trait MapBasedSyncDestination extends SyncDestination {
    protected def trySyncKeyFrom(key: String): Option[String]

    protected def trySyncAllFrom(): Map[String, String]

    protected def trySyncTo(key: String, value: String): Unit
  }

  trait SetBasedSyncDestination extends SyncDestination {


    protected def trySyncAllFrom(): Future[Set[RichInteractionVariableHistorySerialized]]

    protected def trySyncTo(key: String, value: String): Future[Unit]

  }


  private case class SyncStatusOld(
                                    unwrittenHistories: Set[RichInteractionVariableHistorySerialized],
                                    historiesInCache: Set[RichInteractionVariableHistorySerialized],
                                    lastSyncReceived: LocalDateTime = LocalDateTime.now()
                                  ) {


  }

  private case class ClientServerSyncStatus(statusFromClientToServer: SyncStatus, statusFromServerToClient: SyncStatus)

  private sealed trait SyncStatus

  /*
  private sealed trait SyncStatus[T] {
    def elementsKnown: Set[T]
  }*/

  private object SyncStatusUnsynced extends SyncStatus

  private case class SyncStatusSyncedUntil(timestamp: LocalDateTime) extends SyncStatus

  private case object SyncStatusUnknown extends SyncStatus

  private case object SyncStatusEverythingSynced extends SyncStatus

  private case class SyncStatusPartialSync(unwrittenHistories: Set[RichInteractionVariableHistorySerialized]) extends SyncStatus
  
}

