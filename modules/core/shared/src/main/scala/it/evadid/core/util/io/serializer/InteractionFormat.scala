package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*

/** Type-level association between an interaction type and its serializers.
  *
  * Each interaction has exactly one format that provides:
  * - `stateSerializer`: For serializing the interaction state (T)
  * - `historySerializer`: For serializing the interaction history
  * - `exerciseSerializer`: For serializing the interaction element itself
  *
  * InteractionFormat implementations are case objects (the companion objects
  * of interaction classes). Case objects are naturally serializable (they're
  * singletons), making the format serializable. This allows formats to be
  * passed explicitly through method signatures:
  * - Portable across server boundaries (no elementId lookup needed)
  * - Explicit in method signatures (type-safe)
  * - Easy to test (pass format as parameter)
  *
  * ==Serialization==
  *
  * InteractionFormat is serialized as a string containing the fully qualified class name.
  * This is necessary because:
  * - Format instances are passed from client to server as part of InteractionHistoryJson
  * - Custom workbooks may use interaction formats not known to the server
  * - The format is needed on the server to deserialize the history JSON
  *
  * Since all formats are known at compile time, the client always has the format.
  * If an unknown format is received, a descriptive formatNotKnown error is thrown.
  *
  * Usage in interaction definitions:
  * {{{
  * case class MyInteraction(...) extends WorkbookInteractionElementFull[State, Grade, Scaffold] {
  *   override def format: InteractionFormat[State, Grade, Scaffold] = MyInteraction
  * }
  * object MyInteraction extends InteractionFormat[State, Grade, Scaffold] {
  *   // formatters derive serializers automatically
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

  /** Creates an InteractionFormat from three individual serializers.
    *
    * This is typically used in interaction companion objects that extend the trait.
    */
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

  /** Read format by class name (for deserialization from stored class name).
    *
    * @param className The fully qualified class name of the format
    * @return The format instance (singleton case object)
    * @throws RuntimeException if the format class is not known
    */
  def fromClassName(className: String): InteractionFormat[?, ?, ?] = {
    try {
      val clazz = Class.forName(className)
      val field = clazz.getField("MODULE$")
      field.get(null).asInstanceOf[InteractionFormat[?, ?, ?]]
    } catch {
      case e: Exception =>
        throw new RuntimeException(
          s"Format class '$className' is not known. " +
          s"This may indicate an unknown interaction type. " +
          s"Error: ${e.getMessage}"
        )
    }
  }

}
