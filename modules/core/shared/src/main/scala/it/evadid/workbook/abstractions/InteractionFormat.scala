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
import it.evadid.workbook.interaction.variable.events.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.model.blockchain.TeachingChain
import it.evadid.workbook.model.compression.CompressionExperiment
import it.evadid.workbook.model.evacuation.{EvacuationExperiment, EvacuationFloorPlan}
import it.evadid.workbook.model.pixel.BinaryPixelImage
import it.evadid.workbook.model.plot.PlotAnswer
import it.evadid.workbook.model.qr.QrCode
import upickle.default.*

/** A workbook-independent, finite association of saved state, feedback and their codecs. */
sealed trait InteractionFormat {
  type InteractionStateType
  type GradingInfoType
  type ScaffoldingInfoType

  def codecs: InteractionFormat.Codecs[this.type]
  final def stateSerializer: Serializer[InteractionFormat.State[this.type]] = codecs.stateSerializer
  final def gradingInfoSerializer: Serializer[InteractionFormat.Grading[this.type]] = codecs.gradingInfoSerializer
  final def scaffoldingInfoSerializer: Serializer[InteractionFormat.Scaffolding[this.type]] = codecs.scaffoldingInfoSerializer
  final def historySerializer: Serializer[InteractionVariableHistory[this.type]] = codecs.historySerializer
  final def name: String = getClass.getSimpleName.stripSuffix("$")
}

object InteractionFormat {
  type State[F <: InteractionFormat] = F match { case Typed[s, g, a] => s }
  type Grading[F <: InteractionFormat] = F match { case Typed[s, g, a] => g }
  type Scaffolding[F <: InteractionFormat] = F match { case Typed[s, g, a] => a }

  /** Only format definitions use three parameters; consumers carry the format singleton. */
  sealed abstract class Typed[S: ReadWriter, G: ReadWriter, A: ReadWriter] extends InteractionFormat {
    final type InteractionStateType = S
    final type GradingInfoType = G
    final type ScaffoldingInfoType = A
    final lazy val codecs: Codecs[this.type] = new Codecs[this.type](summon[ReadWriter[S]], summon[ReadWriter[G]], summon[ReadWriter[A]])
  }

  /** The singleton value determines F. Scala does not preserve that equality when widening
    * a method argument, so the invariant codec is narrowed once at this boundary.
    */
  def codecsFor[F <: InteractionFormat](format: F): Codecs[F] =
    format.codecs.asInstanceOf[Codecs[F]]

  inline def instance[F <: InteractionFormat](using value: ValueOf[F]): F = value.value

  final class Codecs[F <: InteractionFormat](
      givenState: ReadWriter[State[F]], givenGrading: ReadWriter[Grading[F]], givenScaffolding: ReadWriter[Scaffolding[F]]) {
    private given stateRW: ReadWriter[State[F]] = givenState
    private given gradingRW: ReadWriter[Grading[F]] = givenGrading
    private given scaffoldingRW: ReadWriter[Scaffolding[F]] = givenScaffolding
    private given answerRW: ReadWriter[AnswerChangedEvent[F]] = macroRW
    private given gradingStartedRW: ReadWriter[GradingStartedEvent[F]] = macroRW
    private given gradingCompletedRW: ReadWriter[GradingCompletedEvent[F]] = macroRW
    private given scaffoldingStartedRW: ReadWriter[ScaffoldingStartedEvent[F]] = macroRW
    private given scaffoldingCompletedRW: ReadWriter[ScaffoldingCompletedEvent[F]] = macroRW
    private given eventRW: ReadWriter[InteractionEvent[F]] = ReadWriter.merge(
      answerRW, gradingStartedRW, gradingCompletedRW, scaffoldingStartedRW, scaffoldingCompletedRW)
    private given eventsRW: ReadWriter[InteractionEvents[F]] = macroRW
    given historyReadWriter: ReadWriter[InteractionVariableHistory[F]] = macroRW

    val stateSerializer: Serializer[State[F]] = Serializer.fromImplicitRW
    val gradingInfoSerializer: Serializer[Grading[F]] = Serializer.fromImplicitRW
    val scaffoldingInfoSerializer: Serializer[Scaffolding[F]] = Serializer.fromImplicitRW
    val eventSerializer: Serializer[InteractionEvent[F]] = Serializer.fromImplicitRW
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  case object TextInteractionFormat extends Typed[String, Unit, Unit]
  case object LabeledCheckboxInteractionFormat extends Typed[Boolean, Unit, Unit]
  case object MessagingInteractionFormat extends Typed[MessengerModelScaffolding, Unit, Unit]
  case object TextInputWithLlmGradingAndScaffoldingFormat extends Typed[String, MessengerModel, MessengerModel]
  case object ChoiceInteractionFormat extends Typed[ChoiceAnswer, Unit, Unit]
  case object ProgrammingExerciseWithLlmScaffoldingAndGrading extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]
  case object SqlCommandExerciseFormat extends Typed[String, Unit, Unit]
  case object QrCodeInteractionFormat extends Typed[QrCode, Unit, Unit]
  case object BinaryPixelInteractionFormat extends Typed[BinaryPixelImage, Unit, Unit]
  case object TurtleProjectFormat extends Typed[TurtleStitchProjectState, Unit, Unit]
  case object CoordinatePlotInteractionFormat extends Typed[PlotAnswer, Unit, Unit]
  case object CodeTaskToggleInteractionFormat extends Typed[CodeTaskToggleState, Unit, Unit]
  case object SketchDownloadInteractionFormat extends Typed[String, Unit, Unit]
  case object MailInteractionFormat extends Typed[InboxStateScaffolding, Unit, Unit]
  case object MailEditorFormat extends Typed[InboxStateScaffolding, Unit, Unit]
  case object AnswerTableInteractionFormat extends Typed[TableAnswer, Unit, Unit]
  case object UnicodeComparisonInteractionFormat extends Typed[UnicodeComparisonAnswer, Unit, Unit] { val maxTextLength = 256 }
  case object BlockchainInteractionFormat extends Typed[TeachingChain, Unit, Unit]
  case object Sha256InteractionFormat extends Typed[Sha256Answer, Unit, Unit]
  case object SquareMiddleHashInteractionFormat extends Typed[SquareMiddleHashAnswer, Unit, Unit]
  case object ReorderCodeInteractionFormat extends Typed[ReorderInteractionState[String], Unit, Unit]
  case object ReorderMapIdInteractionFormat extends Typed[ReorderInteractionState[LanguageMapContentId], Unit, Unit]
  case object SortingInteractionFormat extends Typed[SortingInteractionState, Unit, Unit]
  case object SortingReasonInteractionFormat extends Typed[SortingReasonInteractionState, Unit, Unit]
  case object SlideshowFormat extends Typed[SlideshowState, Unit, Unit]
  case object EvacuationConstructFloorInteractionFormat extends Typed[EvacuationFloorPlan, Unit, Unit]
  case object ThresholdNeuronInteractionFormat extends Typed[NeuronParameters, Unit, Unit]
  case object CompressionExperimentInteractionFormat extends Typed[CompressionExperiment, Unit, Unit]
  case object TurtleRecreateShapeInteractionFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]
  case object ProgrammingExerciseFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]
  case object CategorizationInteractionFormat extends Typed[String, Unit, Unit]
  case object ChoiceSelectionInteractionFormat extends Typed[ChoiceSelectionState, Unit, Unit]
  case object MatchingInteractionFormat extends Typed[MatchingInteractionState, Unit, Unit]
  case object FillInBlanksInteractionFormat extends Typed[FillInBlanksState, Unit, Unit]
  case object TableFillInInteractionFormat extends Typed[TableFillInState, Unit, Unit]
  case object DropdownBlanksInteractionFormat extends Typed[DropdownBlanksState, Unit, Unit]
  case object EvacuationExperimentFormat extends Typed[EvacuationExperiment, Unit, Unit]

  val knownFormats: List[InteractionFormat] = List(
    TextInteractionFormat,
    LabeledCheckboxInteractionFormat,
    MessagingInteractionFormat,
    TextInputWithLlmGradingAndScaffoldingFormat,
    ChoiceInteractionFormat,
    ProgrammingExerciseWithLlmScaffoldingAndGrading,
    SqlCommandExerciseFormat,
    QrCodeInteractionFormat,
    BinaryPixelInteractionFormat,
    TurtleProjectFormat,
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
    ProgrammingExerciseFormat,
    CategorizationInteractionFormat,
    ChoiceSelectionInteractionFormat,
    MatchingInteractionFormat,
    FillInBlanksInteractionFormat,
    TableFillInInteractionFormat,
    DropdownBlanksInteractionFormat,
    EvacuationExperimentFormat
  )

  def fromName(name: String): InteractionFormat =
    knownFormats.find(_.name == name).getOrElse(throw new IllegalArgumentException(s"Unknown interaction format '$name'"))

  given ReadWriter[InteractionFormat] = readwriter[String].bimap(_.name, fromName)
}
