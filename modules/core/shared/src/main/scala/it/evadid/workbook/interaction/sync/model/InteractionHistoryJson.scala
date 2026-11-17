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
  * Type parameters:
  * - T: The interaction state type (e.g., String, ProgrammingState)
  * - GA: The grading result type (typically Unit for no grading)
  * - SA: The scaffolding result type (typically Unit for no scaffolding)
  *
  * The format field contains the InteractionFormat case object which provides
  * type-safe serializers for the interaction. Since InteractionFormat derives
  * ReadWriter, it is automatically serializable (stores class name string).
  *
  * The format is passed explicitly and is necessary for the receiving server to
  * know how to deserialize the history JSON (e.g., for custom workbooks with
  * unknown interaction formats).
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
      format.historySerializer.deserialize(historyJson)
    }

}

object InteractionHistoryJson {


}
