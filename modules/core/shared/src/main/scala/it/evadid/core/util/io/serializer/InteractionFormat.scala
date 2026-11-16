package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*

/** Type-level association between an interaction type and its serializers.
  *
  * Each interaction should have exactly one format that provides:
  * - `stateSerializer`: For serializing the interaction state (T)
  * - `historySerializer`: For serializing the interaction history
  * - `exerciseSerializer`: For serializing the interaction element itself
  *
  * Unlike registry-based approaches, InteractionFormat is a **serializable object**
  * that can be passed explicitly through method signatures. This makes it:
  * - Portable across server boundaries (no elementId lookup needed)
  * - Explicit in method signatures (type-safe)
  * - Easy to test (pass format as parameter)
  *
  * Usage in interaction definitions:
  * {{{
  * case class MyInteraction(...) extends WorkbookInteractionElementFull[State, Grade, Scaffold] {
  *   override def format: InteractionFormat[State, Grade, Scaffold] = MyInteraction.format
  * }
  * object MyInteraction extends InteractionFormat {
  *   // format is provided by the trait
  * }
  * }}}
  *
  * Usage in backend methods:
  * {{{
  * // Format is passed explicitly, no registry lookup needed
  * def processHistory(format: InteractionFormat[State, Grade, Scaffold], json: String): History =
  *   format.historySerializer.deserialize(json)
  * }}}
  */
trait InteractionFormat[T, GA, SA] {

  /** Serializer for the interaction state (T).
    *
    * Used to serialize/deserialize the current state value of an interaction.
    * Example: programming code, text input, choice selections.
    */
  def stateSerializer: Serializer[T]

  /** Serializer for the interaction history.
    *
    * Used to serialize/deserialize the complete history of state changes,
    * grading results, and scaffolding events.
    */
  def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]]

  /** Serializer for the workbook interaction element.
    *
    * Used to serialize the interaction definition itself (elementId, configuration).
    */
  def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]

}

object InteractionFormat {

  /** Creates an InteractionFormat from three individual serializers. */
  def apply[T, GA, SA](
      stateSer: Serializer[T],
      historySer: Serializer[InteractionVariableHistory[T, GA, SA]],
      exerciseSer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
  ): InteractionFormat[T, GA, SA] =
    new InteractionFormat[T, GA, SA] {
      override def stateSerializer: Serializer[T] = stateSer
      override def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]] = historySer
      override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]] = exerciseSer
    }

  /** Creates an InteractionFormat using implicit ReadWriter instances.
    *
    * This is the most common way to create a format when your types have
    * upickle ReadWriter instances available (typically via `derives ReadWriter`).
    */
  def fromReadWriters[T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: InteractionFormat[T, GA, SA] =
    apply(
      Serializer.fromImplicitRW[T],
      Serializer.fromImplicitRW[InteractionVariableHistory[T, GA, SA]],
      Serializer.fromImplicitRW[WorkbookInteractionElementFull[T, GA, SA]]
    )

}
