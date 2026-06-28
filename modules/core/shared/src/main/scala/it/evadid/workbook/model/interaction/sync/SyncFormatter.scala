package it.evadid.workbook.model.interaction.sync

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.model.interaction.sync.SyncFormatter.*
import it.evadid.workbook.model.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}

import java.time.LocalDateTime

sealed trait SyncFormatter {
  def serialize(request: InteractionSyncRequest): String

  def deserialize(serialized: String): InteractionVariableHistorySerialized

  def tryDeserialize(serialized: String): Option[InteractionVariableHistorySerialized] =
    try Some(deserialize(serialized)) catch case e: Throwable => {
      println(s"[WARN] from ${getClass.getName}: cannot deserialize: $serialized: ${e.getMessage}")
      None
    }

}

object SyncFormatter {

  case class RichInteractionVariableFormatter() extends SyncFormatter {

    override def serialize(request: InteractionSyncRequest): String =   {
      val lastStateSer: InteractionVariableStateSerialized = request.history.lastState
      val rich = RichInteractionVariableHistorySerialized(request.syncContext.keyForSerialisation, lastStateSer.timestamp, lastStateSer.serializedValue, request.history)
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
    def serialize(request: InteractionSyncRequest): String = {
      serializerHistory.serialize(request.history)
    }

    def deserialize(serialized: String): InteractionVariableHistorySerialized = {
      serializerHistory.deserialize(serialized)
    }
  }

}
