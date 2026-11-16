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
  * ==Format Storage==
  *
  * All known format implementations are stored in the companion object's
  * `knownFormats` set:
  * {{{val knownFormats: Set[InteractionFormat[?, ?, ?]] = Set(...)
  * }}}
  *
  * This provides type-safe access to all formats and ensures all known
  * interaction types are registered at compile time.
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

  /** ReadWriter for InteractionFormat.
    *
    * Format instances are case objects (singletons). We serialize by storing
    * the fully qualified class name and deserialize by loading the singleton.
    */
  given formatReadWriter[T, GA, SA]: ReadWriter[InteractionFormat[T, GA, SA]] =
    upickle.default.readwriter[ujson.Value].bimap[
      InteractionFormat[T, GA, SA]
    ](
      format => ujson.Str(format.getClass.getName),
      value => {
        val className = value.str
        try {
          val clazz = Class.forName(className)
          val field = clazz.getField("MODULE$")
          field.get(null).asInstanceOf[InteractionFormat[T, GA, SA]]
        } catch {
          case e: Exception =>
            throw new RuntimeException(
              s"Format class '$className' is not known. " +
              s"This may indicate an unknown interaction type. " +
              s"Error: ${e.getMessage}"
            )
        }
      }
    )

  /** Set of all known InteractionFormat implementations.
    *
    * This contains all format case objects that are known at compile time.
    * It's used for type-safe format lookup and validation.
    */
  val knownFormats: Set[InteractionFormat[?, ?, ?]] = Set(
    // Basic interactions
    it.evadid.workbook.elements.interactionElements.basic.TextInteraction,
    it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction,
    it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction,
    it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction,
    
    // Choice interactions
    it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction,
    
    // SQL interactions
    it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise,
    
    // QR code interactions
    it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction,
    
    // Pixel interactions
    it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction,
    
    // Plot interactions
    it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction,
    
    // CodeTaskToggle interactions
    it.evadid.workbook.elements.interactionElements.codeTaskToggle.CodeTaskToggleInteraction,
    it.evadid.workbook.elements.interactionElements.codeTaskToggle.SketchDownloadInteraction,
    
    // EmailSimulator interactions
    it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction,
    it.evadid.workbook.elements.interactionElements.emailSimulator.MailEditor,
    
    // Table interactions
    it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction,
    
    // Text interactions
    it.evadid.workbook.elements.interactionElements.text.UnicodeComparisonInteraction,
    
    // Blockchain interactions
    it.evadid.workbook.elements.interactionElements.blockchain.BlockchainInteraction,
    it.evadid.workbook.elements.interactionElements.blockchain.Sha256Interaction,
    it.evadid.workbook.elements.interactionElements.blockchain.SquareMiddleHashInteraction,
    
    // Reorder interactions
    it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderCodeInteraction,
    it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction,
    
    // Sorting interactions
    it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteraction,
    it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.SortingReasonInteraction,
    
    // Slideshow interactions
    it.evadid.workbook.elements.interactionElements.slideshow.Slideshow,
    
    // Neuron interactions
    it.evadid.workbook.elements.interactionElements.neuron.ThresholdNeuronInteraction,
    
    // Evacuation interactions
    it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction,
    it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction,
    
    // Compression interactions
    it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction,
    
    // Programming interactions
    it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
  )

  /** Read format by class name (for deserialization from stored class name).
    *
    * @param className The fully qualified class name of the format
    * @return The format instance (singleton case object)
    * @throws RuntimeException if the format class is not known
    */
  def fromClassName(className: String): InteractionFormat[?, ?, ?] = {
    val format = knownFormats.find(_.getClass.getName == className)
    format.getOrElse {
      throw new RuntimeException(
        s"Format class '$className' is not known. " +
        s"This may indicate an unknown interaction type. " +
        s"Known formats: ${knownFormats.map(_.getClass.getName).mkString(", ")}"
      )
    }
  }

}
