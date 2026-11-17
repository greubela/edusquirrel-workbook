package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.scaffolding.AdditionalInfoLlmScaffolding
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.elements.interactionElements.blockchain.{Sha256Answer, SquareMiddleHashAnswer}
import it.evadid.workbook.elements.interactionElements.choice.ChoiceAnswer
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.CodeTaskToggleState
import it.evadid.workbook.elements.interactionElements.emailSimulator.{InboxState, InboxStateScaffolding}
import it.evadid.workbook.elements.interactionElements.neuron.NeuronParameters
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleStitchProjectState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteractionState
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowState
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteractionState
import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.SortingReasonInteractionState
import it.evadid.workbook.elements.interactionElements.table.TableAnswer
import it.evadid.workbook.elements.interactionElements.text.UnicodeComparisonAnswer
import it.evadid.workbook.elements.interactionElements.todo.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.model.blockchain.TeachingChain
import it.evadid.workbook.model.compression.CompressionExperiment
import it.evadid.workbook.model.evacuation.{EvacuationExperiment, EvacuationFloorPlan}
import it.evadid.workbook.model.pixel.BinaryPixelImage
import it.evadid.workbook.model.plot.PlotAnswer
import it.evadid.workbook.model.qr.QrCode
import upickle.default.*

/** Type-level association between an interaction type and its serializers.
  *
  * Each interaction has exactly one format that provides:
  * - `stateSerializer`: For serializing the interaction state (InteractionStateType)
  * - `gradingInfoSerializer`: For serializing grading info (GradingInfoType)
  * - `scaffoldingInfoSerializer`: For serializing scaffolding info (ScaffoldingInfoType)
  *
  * InteractionFormat is a sealed trait with case objects as implementations.
  * All format implementations are defined in the companion object of this trait
  * for compile-time type safety.
  *
  * Case objects are naturally serializable (they're singletons), making the
  * format serializable. This allows formats to be passed explicitly through
  * method signatures.
  */
sealed trait InteractionFormat {

  type InteractionStateType
  type GradingInfoType
  type ScaffoldingInfoType

  // The historySerializer is defined as a method that must be implemented by each format object.
  // Each format object sets its own type F and provides the correct serializer for InteractionVariableHistory[F].
  type F <: InteractionFormat
  def historySerializer: Serializer[InteractionVariableHistory[F]]

  type Format = this.type
}

sealed trait GradingType {
  type InteractionStateType
  type GradingInfoType
}

object InteractionFormat {

  inline def instance[F <: InteractionFormat](using v: ValueOf[F]): F = v.value

  /** Get the format object from its class name. Returns None if the class is not a known format. */
  def fromClassName(className: String): Option[InteractionFormat] = {
    knownFormats.find(_.getClass.getName == className)
  }

  // All known format implementations (for runtime type resolution)
  val knownFormats: List[InteractionFormat] = List(
    TextInteractionFormat,
    LabeledNumberInteractionFormat,
    LabeledCheckboxInteractionFormat,
    MessagingInteractionFormat,
    TextInputWithLlmGradingAndScaffoldingFormat,
    ChoiceInteractionFormat,
    ProgrammingExerciseWithLlmScaffoldingAndGrading,
    SqlCommandExerciseFormat,
    QrCodeInteractionFormat,
    BinaryPixelInteractionFormat,
    LegacyTurtleFormat,
    CoordinatePlotInteractionFormat,
    CodeTaskToggleInteractionFormat,
    SketchDownloadInteractionFormat,
    MailInteractionFormat,
    MailEditorFormat,
    AnswerTableInteractionFormat,
    UnicodeComparisonInteractionFormat,
    BlockchainInteractionFormat,
    Sha256InteractionFormat,
    SquareMiddleHashInteractionFormat,
    ReorderCodeInteractionFormat,
    ReorderMapIdInteractionFormat,
    SortingInteractionFormat,
    SortingReasonInteractionFormat,
    SlideshowFormat,
    EvacuationConstructFloorInteractionFormat,
    ThresholdNeuronInteractionFormat,
    CompressionExperimentInteractionFormat,
    TurtleRecreateShapeInteractionFormat,
    TurtleStitchRecreateShapeInteractionLegacyFormat,
    TextInputWithScaffoldingAndGradingFormat,
    ProgrammingExerciseFormat,
    CategorizationInteractionFormat,
    ChoiceSelectionInteractionFormat,
    MatchingInteractionFormat,
    FillInBlanksInteractionFormat,
    TableFillInInteractionFormat,
    DropdownBlanksInteractionFormat,
    EvacuationExperimentFormat
  )

  // ============================================================================
  // All format implementations (case objects) for interactions
  // Format objects end with "Format" to distinguish from interaction elements
  // ============================================================================

  // Basic interactions
  object TextInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TextInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }


  object LabeledNumberInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LabeledNumberInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object LabeledCheckboxInteractionFormat extends InteractionFormat {
    type InteractionStateType = Boolean
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LabeledCheckboxInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object MessagingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MessengerModelScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MessagingInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object TextInputWithLlmGradingAndScaffoldingFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = MessengerModel
    type ScaffoldingInfoType = MessengerModel
    type F = TextInputWithLlmGradingAndScaffoldingFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }


  // Choice interactions
  object ChoiceInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ChoiceInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object ProgrammingExerciseWithLlmScaffoldingAndGrading extends InteractionFormat{
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = AdditionalInfoLlmScaffolding
    type F = ProgrammingExerciseWithLlmScaffoldingAndGrading.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // SQL interactions
  object SqlCommandExerciseFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SqlCommandExerciseFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // QR code interactions
  object QrCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = QrCode
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = QrCodeInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // Pixel interactions
  object BinaryPixelInteractionFormat extends InteractionFormat {
    type InteractionStateType = BinaryPixelImage
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = BinaryPixelInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }


  // remove as soon as possible
  object LegacyTurtleFormat extends InteractionFormat {
    type InteractionStateType = TurtleStitchProjectState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LegacyTurtleFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // Plot interactions
  object CoordinatePlotInteractionFormat extends InteractionFormat {
    type InteractionStateType = PlotAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CoordinatePlotInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // CodeTaskToggle interactions
  object CodeTaskToggleInteractionFormat extends InteractionFormat {
    type InteractionStateType = CodeTaskToggleState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CodeTaskToggleInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object SketchDownloadInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SketchDownloadInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // EmailSimulator interactions
  object MailInteractionFormat extends InteractionFormat {
    type InteractionStateType = InboxStateScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MailInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object MailEditorFormat extends InteractionFormat {
    type InteractionStateType = InboxStateScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MailEditorFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // Table interactions
  object AnswerTableInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = AnswerTableInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // Text interactions
  object UnicodeComparisonInteractionFormat extends InteractionFormat {
    type InteractionStateType = UnicodeComparisonAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 256
    type F = UnicodeComparisonInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // Blockchain interactions
  object BlockchainInteractionFormat extends InteractionFormat {
    type InteractionStateType = TeachingChain
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = BlockchainInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  object Sha256InteractionFormat extends InteractionFormat {
    type InteractionStateType = Sha256Answer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 4096
    type F = Sha256InteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  object SquareMiddleHashInteractionFormat extends InteractionFormat {
    type InteractionStateType = SquareMiddleHashAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SquareMiddleHashInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // Reorder interactions
  object ReorderCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[String]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ReorderCodeInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object ReorderMapIdInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[LanguageMapContentId]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ReorderMapIdInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  // Sorting interactions
  object SortingInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SortingInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object SortingReasonInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingReasonInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SortingReasonInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  // Slideshow interactions
  object SlideshowFormat extends InteractionFormat {
    type InteractionStateType = SlideshowState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SlideshowFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  // Evacuation interactions
  object EvacuationConstructFloorInteractionFormat extends InteractionFormat {
    type InteractionStateType = EvacuationFloorPlan
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = EvacuationConstructFloorInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object ThresholdNeuronInteractionFormat extends InteractionFormat {
    type InteractionStateType = NeuronParameters
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ThresholdNeuronInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // Compression interactions
  object CompressionExperimentInteractionFormat extends InteractionFormat {


    type InteractionStateType = CompressionExperiment
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CompressionExperimentInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  // Programming interactions
  object TurtleRecreateShapeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit
    type F = TurtleRecreateShapeInteractionFormat.type


    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object TurtleStitchRecreateShapeInteractionLegacyFormat extends InteractionFormat {
    type InteractionStateType = TurtleStitchProjectState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TurtleStitchRecreateShapeInteractionLegacyFormat.type


    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object TextInputWithScaffoldingAndGradingFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TextInputWithScaffoldingAndGradingFormat.type


    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object ProgrammingExerciseFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit
    type F = ProgrammingExerciseFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }


  // Todo interactions
  object CategorizationInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CategorizationInteractionFormat.type


    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object ChoiceSelectionInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceSelectionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ChoiceSelectionInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object MatchingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MatchingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MatchingInteractionFormat.type

    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)

  }

  object FillInBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = FillInBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = FillInBlanksInteractionFormat.type


    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)


  }

  object TableFillInInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableFillInState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TableFillInInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

  object DropdownBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = DropdownBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = DropdownBlanksInteractionFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }


  object EvacuationExperimentFormat extends InteractionFormat {
    type InteractionStateType = EvacuationExperiment
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = EvacuationExperimentFormat.type
    def historySerializer(using rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]] = Serializer.fromUpickleJson(rw)
  }

}
