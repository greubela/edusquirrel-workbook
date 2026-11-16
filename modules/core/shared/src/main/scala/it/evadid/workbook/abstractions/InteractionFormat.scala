package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
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

  def historySerializer: Serializer[InteractionVariableHistory[this.type]] = Serializer.fromImplicitRW

  type Format = this.type
}

object InteractionFormat {

  inline def instance[F <: InteractionFormat](using v: ValueOf[F]): F = v.value

  // ============================================================================
  // All format implementations (case objects) for interactions
  // Format objects end with "Format" to distinguish from interaction elements
  // ============================================================================

  // Basic interactions
  object TextInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }


  object LabeledNumberInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object LabeledCheckboxInteractionFormat extends InteractionFormat {
    type InteractionStateType = Boolean
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object MessagingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MessengerModelScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object TextInputWithLlmGradingAndScaffoldingFormat extends InteractionFormat {

    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }


  // Choice interactions
  object ChoiceInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // SQL interactions
  object SqlCommandExerciseFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // QR code interactions
  object QrCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = QrCode
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // Pixel interactions
  object BinaryPixelInteractionFormat extends InteractionFormat {
    type InteractionStateType = BinaryPixelImage
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }


  // Plot interactions
  object CoordinatePlotInteractionFormat extends InteractionFormat {
    type InteractionStateType = PlotAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // CodeTaskToggle interactions
  object CodeTaskToggleInteractionFormat extends InteractionFormat {
    type InteractionStateType = CodeTaskToggleState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object SketchDownloadInteractionFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // EmailSimulator interactions
  object MailInteractionFormat extends InteractionFormat {
    type InteractionStateType = InboxStateScaffolding
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object MailEditorFormat extends InteractionFormat {
    type InteractionStateType = InboxState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // Table interactions
  object AnswerTableInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // Text interactions
  object UnicodeComparisonInteractionFormat extends InteractionFormat {
    type InteractionStateType = UnicodeComparisonAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 256
  }

  // Blockchain interactions
  object BlockchainInteractionFormat extends InteractionFormat {
    type InteractionStateType = TeachingChain
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }

  object Sha256InteractionFormat extends InteractionFormat {
    type InteractionStateType = Sha256Answer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


    val maxTextLength = 4096
  }

  object SquareMiddleHashInteractionFormat extends InteractionFormat {
    type InteractionStateType = SquareMiddleHashAnswer
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }

  // Reorder interactions
  object ReorderCodeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[String]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object ReorderMapIdInteractionFormat extends InteractionFormat {
    type InteractionStateType = ReorderInteractionState[LanguageMapContentId]
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  // Sorting interactions
  object SortingInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object SortingReasonInteractionFormat extends InteractionFormat {
    type InteractionStateType = SortingReasonInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  // Slideshow interactions
  object SlideshowFormat extends InteractionFormat {
    type InteractionStateType = SlideshowState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  // Evacuation interactions
  object EvacuationConstructFloorInteractionFormat extends InteractionFormat {
    type InteractionStateType = EvacuationFloorPlan
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object ThresholdNeuronInteraction extends InteractionFormat {
    type InteractionStateType = NeuronParameters
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }

  // Compression interactions
  object CompressionExperimentInteractionFormat extends InteractionFormat {



  }

  // Programming interactions
  object TurtleRecreateShapeInteractionFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit


  }

  object TurtleStitchRecreateShapeInteractionLegacyFormat extends InteractionFormat {
    type InteractionStateType = TurtleStitchProjectState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object TextInputWithScaffoldingAndGradingFormat extends InteractionFormat {
    type InteractionStateType = String
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object ProgrammingExerciseFormat extends InteractionFormat {
    type InteractionStateType = ProgrammingState
    type GradingInfoType = AdditionalProgrammingTestInfo
    type ScaffoldingInfoType = Unit
  }


  // Todo interactions
  object CategorizationInteractionFormat extends InteractionFormat {
    type InteractionStateType = CategorizationInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object ChoiceSelectionInteractionFormat extends InteractionFormat {
    type InteractionStateType = ChoiceSelectionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object MatchingInteractionFormat extends InteractionFormat {
    type InteractionStateType = MatchingInteractionState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit

  }

  object FillInBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = FillInBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit


  }

  object TableFillInInteractionFormat extends InteractionFormat {
    type InteractionStateType = TableFillInState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }

  object DropdownBlanksInteractionFormat extends InteractionFormat {
    type InteractionStateType = DropdownBlanksState
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }


  object EvacuationExperimentFormat extends InteractionFormat {
    type InteractionStateType = EvacuationExperiment
    type GradingInfoType = Unit
    type ScaffoldingInfoType = Unit
  }

}
