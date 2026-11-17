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
  def historySerializer(implicit rw: ReadWriter[InteractionVariableHistory[F]]): Serializer[InteractionVariableHistory[F]]

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
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }


  object LabeledNumberInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LabeledNumberInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object LabeledCheckboxInteractionFormat extends InteractionFormat {
    type InteractionStateType = Boolean
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LabeledCheckboxInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object MessagingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MessengerModelScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MessagingInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object TextInputWithLlmGradingAndScaffoldingFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = MessengerModel
    type ScaffoldingInfoType = MessengerModel
    type F = TextInputWithLlmGradingAndScaffoldingFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }


  // Choice interactions
  object ChoiceInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ChoiceInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object ProgrammingExerciseWithLlmScaffoldingAndGrading extends InteractionFormat{
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = AdditionalInfoLlmScaffolding
    type F = ProgrammingExerciseWithLlmScaffoldingAndGrading.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // SQL interactions
  object SqlCommandExerciseFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SqlCommandExerciseFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // QR code interactions
  object QrCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = QrCode
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = QrCodeInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // Pixel interactions
  object BinaryPixelInteractionFormat extends InteractionFormat {
    type InteractionStateType = BinaryPixelImage
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = BinaryPixelInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }


  // remove as soon as possible
  object LegacyTurtleFormat extends InteractionFormat {
    type IneractionStateType = TurtleStitchProjectState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = LegacyTurtleFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // Plot interactions
  object CoordinatePlotInteractionFormat extends InteractionFormat {
    type InteractionStateType = PlotAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CoordinatePlotInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // CodeTaskToggle interactions
  object CodeTaskToggleInteractionFormat extends InteractionFormat {
    type InteractionStateType = CodeTaskToggleState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CodeTaskToggleInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object SketchDownloadInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SketchDownloadInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // EmailSimulator interactions
  object MailInteractionFormat extends InteractionFormat {
    type InteractionStateType = InboxStateScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MailInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object MailEditorFormat extends InteractionFormat {
    type InteractionStateType = InboxStateScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MailEditorFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // Table interactions
  object AnswerTableInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = AnswerTableInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // Text interactions
  object UnicodeComparisonInteractionFormat extends InteractionFormat {
    type InteractionStateType = UnicodeComparisonAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 256
    type F = UnicodeComparisonInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // Blockchain interactions
  object BlockchainInteractionFormat extends InteractionFormat {
    type InteractionStateType = TeachingChain
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = BlockchainInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  object Sha256InteractionFormat extends InteractionFormat {
    type InteractionStateType = Sha256Answer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 4096
    type F = Sha256InteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  object SquareMiddleHashInteractionFormat extends InteractionFormat {
    type InteractionStateType = SquareMiddleHashAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = SquareMiddleHashInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // Reorder interactions
  object ReorderCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[String]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ReorderCodeInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object ReorderMapIdInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[LanguageMapContentId]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ReorderMapIdInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  // Sorting interactions
  object SortingInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SortingInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object SortingReasonInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingReasonInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SortingReasonInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  // Slideshow interactions
  object SlideshowFormat extends InteractionFormat {
    type InteractionStateType = SlideshowState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = SlideshowFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  // Evacuation interactions
  object EvacuationConstructFloorInteractionFormat extends InteractionFormat {
    type InteractionStateType = EvacuationFloorPlan
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    type F = EvacuationConstructFloorInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object ThresholdNeuronInteractionFormat extends InteractionFormat {
    type InteractionStateType = NeuronParameters
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ThresholdNeuronInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // Compression interactions
  object CompressionExperimentInteractionFormat extends InteractionFormat {


    type InteractionStateType = CompressionExperiment
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CompressionExperimentInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  // Programming interactions
  object TurtleRecreateShapeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit
    type F = TurtleRecreateShapeInteractionFormat.type


    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object TurtleStitchRecreateShapeInteractionLegacyFormat extends InteractionFormat {
    type InteractionStateType = TurtleStitchProjectState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TurtleStitchRecreateShapeInteractionLegacyFormat.type


    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object TextInputWithScaffoldingAndGradingFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TextInputWithScaffoldingAndGradingFormat.type


    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object ProgrammingExerciseFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit
    type F = ProgrammingExerciseFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }


  // Todo interactions
  object CategorizationInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = CategorizationInteractionFormat.type


    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object ChoiceSelectionInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceSelectionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = ChoiceSelectionInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object MatchingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MatchingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = MatchingInteractionFormat.type

    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW

  }

  object FillInBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = FillInBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = FillInBlanksInteractionFormat.type


    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW


  }

  object TableFillInInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableFillInState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = TableFillInInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  object DropdownBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = DropdownBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = DropdownBlanksInteractionFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }


  object EvacuationExperimentFormat extends InteractionFormat {
    type InteractionStateType = EvacuationExperiment
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
    type F = EvacuationExperimentFormat.type
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

}
