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
  * Usage in interaction definitions:
  * {{{
  * case class MyInteraction(...) extends WorkbookInteractionElementFull[State, Grade, Scaffold] {
  *   override def format: InteractionFormat[State, Grade, Scaffold] = MyInteraction.format
  * }
  * object MyInteraction extends InteractionFormat.Provider[State, Grade, Scaffold] {
  *   override def elementIdPrefix: String = "MyInteraction"
  * }
  * }}}
  *
  * This enables type-safe serializer access without runtime interaction variable instances.
  * On the backend, use InteractionFormat.forElementId to retrieve formats.
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

  // ============================================================================
  // Registry for central format lookup by element ID
  // ============================================================================

  private val registry = scala.collection.mutable.Map[String, InteractionFormat[?, ?, ?]]()

  /** Registers an interaction format by its element ID.
    * Called automatically when mixing in Provider.
    */
  def register[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](
      elementId: String,
      format: InteractionFormat[T, GA, SA]
  ): Unit = {
    registry.put(elementId, format.asInstanceOf[InteractionFormat[?, ?, ?]])
  }

  /** Retrieves a format by element ID.
    *
    * This enables backend-side serialization/deserialization without runtime
    * access to interaction instances.
    *
    * Example:
    * {{{
    * val format = InteractionFormat.forElementId[State, Grade, Scaffold]("MyInteraction")
    * format match {
    *   case Some(f) => f.historySerializer.deserialize(json)
    *   case None => // handle unknown interaction
    * }
    * }}}
    */
  def forElementId[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](elementId: String): Option[InteractionFormat[T, GA, SA]] = {
    registry.get(elementId).map(_.asInstanceOf[InteractionFormat[T, GA, SA]])
  }

  /** Returns all registered element IDs.
    */
  def registeredIds: Set[String] = registry.keySet.toSet

  // ============================================================================
  // Provider trait for automatic registration
  // ============================================================================

  /** Trait that can be mixed into interaction companion objects.
    *
    * Provides the `format` method and automatic registration.
    *
    * Usage:
    * {{{
    * case class MyInteraction(...) extends WorkbookInteractionElementFull[State, Grade, Scaffold] {
    *   override def format: InteractionFormat[State, Grade, Scaffold] = MyInteraction.format
    * }
    * object MyInteraction extends InteractionFormat.Provider[State, Grade, Scaffold] {
    *   override def elementIdPrefix: String = "MyInteraction"
    * }
    * }}}
    */
  trait Provider[T: ReadWriter, GA: ReadWriter, SA: ReadWriter] {

    /** The element ID prefix used for registration.
      * Override this in your interaction object.
      */
    def elementIdPrefix: String

    /** The format instance for this interaction type.
      * Automatically registered when this trait is mixed in.
      */
    lazy val format: InteractionFormat[T, GA, SA] = {
      val f = InteractionFormat.fromReadWriters[T, GA, SA]
      InteractionFormat.register[T, GA, SA](elementIdPrefix, f)
      f
    }
  }

}
