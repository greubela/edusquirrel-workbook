package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*

import scala.reflect.ClassTag

/** Factory for serializers associated with a specific interaction type.
  *
  * This trait centralizes all serialization logic for a given interaction type,
  * enabling backend-side serialization without runtime access to InteractionVariable instances.
  * Each interaction type (e.g., TurtleRecreateShapeInteraction, TextInteraction) should have
  * a corresponding InteractionFormat implementation.
  *
  * @tparam T The interaction state type
  * @tparam GA The grading answer type (often Unit)
  * @tparam SA The scaffolding answer type (often Unit)
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

  /** Internal test helper to clear the registry.
    */
  private[serializer] def clearRegistry(): Unit = registry.clear()

  /** Creates an InteractionFormat from three individual serializers.
    *
    * @param stateSerializer Serializer for the state type T
    * @param historySerializer Serializer for InteractionVariableHistory[T, GA, SA]
    * @param exerciseSerializer Serializer for WorkbookInteractionElementFull[T, GA, SA]
    * @tparam T State type
    * @tparam GA Grading answer type
    * @tparam SA Scaffolding answer type
    */
  def apply[T, GA, SA](
      stateSerializer: Serializer[T],
      historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]],
      exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
  ): InteractionFormat[T, GA, SA] =
    new InteractionFormat[T, GA, SA] {
      override def stateSerializer: Serializer[T] = stateSerializer
      override def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]] = historySerializer
      override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]] = exerciseSerializer
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
  // Registry for interaction formats
  // ============================================================================

  /** Internal registry storing format metadata for type-erased lookup.
    * The format object itself is not stored - only metadata for reconstruction.
    */
  private[serializer] case class FormatMetadata[T, GA, SA](
      stateSerializer: Serializer[T],
      historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]],
      exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
  )

  private[serializer] val registry = scala.collection.mutable.Map[String, FormatMetadata[_, _, _]]()

  /** Registers an interaction format by its element ID.
    *
    * This enables backend-side serialization/deserialization of interaction histories
    * without requiring runtime access to InteractionVariable instances.
    *
    * @param elementId The unique identifier of the interaction element
    * @tparam T State type
    * @tparam GA Grading answer type
    * @tparam SA Scaffolding answer type
    */
  def register[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](
      elementId: String
  ): Unit = {
    val format = fromReadWriters[T, GA, SA]
    val metadata = FormatMetadata(
      stateSerializer = format.stateSerializer,
      historySerializer = format.historySerializer,
      exerciseSerializer = format.exerciseSerializer
    )
    registry.put(elementId, metadata.asInstanceOf[FormatMetadata[_, _, _]])
  }

  /** Registers an interaction format with explicit serializer functions.
    *
    * Use this when you need custom serialization logic rather than automatic derivation.
    *
    * @param elementId The unique identifier of the interaction element
    * @param stateSerializer Serializer for the state type T
    * @param historySerializer Serializer for the history
    * @param exerciseSerializer Serializer for the interaction element
    * @tparam T State type
    * @tparam GA Grading answer type
    * @tparam SA Scaffolding answer type
    */
  def registerWithSerializers[T, GA, SA](
      elementId: String,
      stateSerializer: Serializer[T],
      historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]],
      exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
  ): Unit = {
    val metadata = FormatMetadata(stateSerializer, historySerializer, exerciseSerializer)
    registry.put(elementId, metadata.asInstanceOf[FormatMetadata[_, _, _]])
  }

  /** Retrieves a format by element ID.
    *
    * @param elementId The unique identifier of the interaction element
    * @return Some(format) if registered, None otherwise
    */
  def get[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](elementId: String): Option[InteractionFormat[T, GA, SA]] = {
    registry.get(elementId).map { metadata =>
      val m = metadata.asInstanceOf[FormatMetadata[T, GA, SA]]
      InteractionFormat(m.stateSerializer, m.historySerializer, m.exerciseSerializer)
    }
  }

  /** Returns all registered element IDs.
    */
  def registeredIds: scala.collection.Set[String] = registry.keySet

  // ============================================================================
  // Helper for creating format instances in interaction definitions
  // ============================================================================

  /** Trait that can be mixed into interaction objects to automatically register their format.
    *
    * Usage:
    * {{{
    * case class MyInteraction(...) extends WorkbookInteractionElementFull[State, Grade, Scaffold] derives ReadWriter
    * object MyInteraction extends InteractionFormat.Provider[State, Grade, Scaffold] {
    *   override val elementIdPrefix = "myInteraction"
    * }
    * }}}
    */
  trait Provider[T: ReadWriter, GA: ReadWriter, SA: ReadWriter] {

    /** The prefix used for registration (typically the object name or a unique string).
      * Override this in your interaction object.
      */
    def elementIdPrefix: String

    /** Registers this format with the global registry.
      * Call this in your object's companion object initialization.
      */
    def registerFormat(): Unit = {
      InteractionFormat.register[T, GA, SA](elementIdPrefix)
    }

    /** Creates a format instance for this interaction type.
      */
    def format: InteractionFormat[T, GA, SA] = InteractionFormat.fromReadWriters[T, GA, SA]
  }

  /** Provides type-safe format access for known interaction types.
    * 
    * Use this pattern when you need to handle multiple known interaction types
    * in a type-safe manner without pattern matching on element IDs.
    * 
    * Example:
    * {{{
    * def processHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](
    *     interactionId: String,
    *     serialized: String
    * )(using formatProvider: FormatProvider[T, GA, SA]): InteractionVariableHistory[T, GA, SA] = {
    *   val format = formatProvider.get(interactionId)
    *   format.historySerializer.deserialize(serialized)
    * }
    * }}}
    */
  trait FormatProvider[T: ReadWriter, GA: ReadWriter, SA: ReadWriter] {
    def get(elementId: String): InteractionFormat[T, GA, SA]
  }

  object FormatProvider {
    def apply[T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: FormatProvider[T, GA, SA] = 
      new FormatProvider[T, GA, SA] {
        def get(elementId: String): InteractionFormat[T, GA, SA] = 
          InteractionFormat.get[T, GA, SA](elementId).getOrElse(
            throw new NoSuchElementException(s"No format registered for elementId: $elementId")
          )
      }
  }

}
