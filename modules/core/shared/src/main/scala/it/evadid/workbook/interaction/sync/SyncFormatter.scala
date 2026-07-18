package it.evadid.workbook.interaction.sync

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}

import java.time.LocalDateTime

sealed trait SyncFormatter {
  def serialize(syncContext: SyncContext, interactionVariableHistorySerialized: InteractionVariableHistorySerialized): String

  def deserialize(serialized: String): InteractionVariableHistorySerialized

  def tryDeserialize(serialized: String): Option[InteractionVariableHistorySerialized] =
    try Some(deserialize(serialized)) catch case e: Throwable => {
      None
    }

}

object SyncFormatter {

  case class RichInteractionVariableFormatter() extends SyncFormatter {

    override def serialize(syncContext: SyncContext, interactionVariableHistorySerialized: InteractionVariableHistorySerialized): String = {
      val lastStateSer: InteractionVariableStateSerialized = interactionVariableHistorySerialized.lastStateOption.get
      val rich = RichInteractionVariableHistorySerialized(syncContext.keyForSerialisation, lastStateSer.timestamp, lastStateSer.serializedValue, interactionVariableHistorySerialized)
      richSerializer.serialize(rich)
    }

    override def deserialize(serialized: String): InteractionVariableHistorySerialized = {
      deserializeAsRich(serialized).fullHistory
    }

    def deserializeAsRich(serialized: String): RichInteractionVariableHistorySerialized = {
      richSerializer.deserialize(serialized)
    }

  }

  case class InteractionSyncRequest(
                                     syncContext: SyncContext,
                                     history: InteractionVariableHistorySerialized
                                   )

  // RICH
  case class RichInteractionVariableHistorySerialized(keyForSerialisation: String, lastUpdate: LocalDateTime, lastValue: String, fullHistory: InteractionVariableHistorySerialized) {
    //lazy val fullHistorySerialized: String = DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors.serialize(fullHistory)
  }

  private given ivhs: upickle.ReadWriter[InteractionVariableHistorySerialized] = DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors.uPickleReadWrite

  private given ldts: upickle.ReadWriter[LocalDateTime] = DefaultSerializer.serializerLocalDateTimeString.uPickleReadWrite

  private given rvhs: upickle.ReadWriter[RichInteractionVariableHistorySerialized] = upickle.macroRW

  private val serializerHistory: Serializer[InteractionVariableHistorySerialized] = DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors

  private val richSerializer: Serializer[RichInteractionVariableHistorySerialized] = Serializer.fromUpickleJson(rvhs)

  // Default
  val serializeHistory: SyncFormatter = new SyncFormatter() {
    override def serialize(syncContext: SyncContext, interactionVariableHistorySerialized: InteractionVariableHistorySerialized): String = {
      serializerHistory.serialize(interactionVariableHistorySerialized)
    }

    def deserialize(serialized: String): InteractionVariableHistorySerialized = {
      serializerHistory.deserialize(serialized)
    }
  }

}
