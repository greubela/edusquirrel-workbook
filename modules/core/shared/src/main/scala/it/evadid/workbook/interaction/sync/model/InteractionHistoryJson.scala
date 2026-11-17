package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.*
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

import java.time.LocalDateTime
import upickle.default.*

/** Strongly typed wrapper for interaction history JSON with its format.
  *
  * This provides a strongly-typed way to handle history serialization/deserialization
  * instead of passing raw JSON strings throughout the sync pipeline.
  *
  * The format field contains the InteractionFormat case object which provides
  * type-safe serializers for the interaction. Since InteractionFormat derives
  * ReadWriter, it is automatically serializable (stores class name string).
  *
  * The format is passed explicitly and is necessary for the receiving server to
  * know how to deserialize the history JSON (e.g., for custom workbooks with
  * unknown interaction formats).
  *
  * All operations on history should be done through toTyped() which gives
  * access to InteractionVariableHistory with all its methods (merge, timestampLastChanged, etc.)
  */
case class InteractionHistoryJson[F <: InteractionFormat](
    format: F,
    historyJson: String
) derives ReadWriter {

  /** Deserialize the history using the provided format's serializer.
    *
    * @return The typed InteractionVariableHistory
    * @throws ParsingException if the JSON doesn't match the expected format
    */
  def toTyped: InteractionVariableHistory[F] =
    ParsingContext("interactionVariableHistory", historyJson) {
      format.historySerializer.deserialize(historyJson).asInstanceOf[InteractionVariableHistory[F]]
    }

}

object InteractionHistoryJson {

  /** Create an InteractionHistoryJson from an interaction's format and history.
    *
    * @param format The InteractionFormat for the interaction
    * @param history The InteractionVariableHistory to serialize
    * @return An InteractionHistoryJson with the serialized history
    */
  def fromHistory[F <: InteractionFormat](format: F, history: InteractionVariableHistory[F])(using v: ValueOf[F]): InteractionHistoryJson[F] = {
    InteractionHistoryJson(format, format.historySerializer.serialize(history.asInstanceOf[InteractionVariableHistory[format.F]]))
  }

  /** Merge multiple histories into one. This is a legacy method for compatibility;
    * in the new type system, merging requires knowing the format.
    */
  def merge(format: InteractionFormat, histories: List[String]): String = {
    val initial = InteractionVariableHistory.empty[InteractionFormat]
    val merged = histories.foldLeft(initial) { (current, json) =>
      val decoded = format.historySerializer.deserialize(json).asInstanceOf[InteractionVariableHistory[InteractionFormat]]
      current.mergeWith(decoded)
    }
    format.historySerializer.serialize(merged.asInstanceOf[InteractionVariableHistory[format.F]])
  }

}
