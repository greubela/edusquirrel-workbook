package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.scaffolding.AdditionalInfoLlmScaffolding
import it.evadid.workbook.interactions.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.interactions.blockchain.{Sha256Answer, SquareMiddleHashAnswer}
import it.evadid.workbook.interactions.codeTaskToggle.CodeTaskToggleState
import it.evadid.workbook.interactions.emailSimulator.{InboxState, InboxStateScaffolding}
import it.evadid.workbook.interactions.neuron.NeuronParameters
import it.evadid.workbook.interactions.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleStitchProjectState
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.interactions.reorderExercise.ReorderInteractionState
import it.evadid.workbook.interactions.slideshow.SlideshowState
import it.evadid.workbook.interactions.sorting.sortingExercise.SortingInteractionState
import it.evadid.workbook.interactions.sorting.sortingReasonExercise.SortingReasonInteractionState
import it.evadid.workbook.interactions.table.TableAnswer
import it.evadid.workbook.interactions.text.UnicodeComparisonAnswer
import it.evadid.workbook.interactions.todo.*
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.history.events.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.interactions.blockchain.TeachingChain
import it.evadid.workbook.interactions.compression.CompressionExperiment
import it.evadid.workbook.interactions.evacuation.{EvacuationExperiment, EvacuationFloorPlan}
import it.evadid.workbook.interactions.pixel.BinaryPixelImage
import it.evadid.workbook.interactions.plot.PlotAnswer
import it.evadid.workbook.interactions.qr.QrCode
import it.evadid.workbook.abstractions.grading.{GradingResult, WorkbookInteractionGrader}
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.ChoiceState
import it.evadid.workbook.interactions.gpt.TextInputLlmGrader
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{ProgramStructureGrader, RecreateShapeGrader, TurtleCommandUsageGrader}
import it.evadid.workbook.interactions.slideshow.SlideshowGrader
import it.evadid.workbook.interactions.choice.ChoiceGrader
import it.evadid.workbook.interactions.reorderExercise.{ReorderCodeGrader, ReorderMapIdGrader}
import it.evadid.workbook.interactions.matching.{MatchingGrader, MatchingInteractionState}
import it.evadid.workbook.interactions.table.{TableAnswer, TableCellGrader}
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
  /** The stable name histories are tagged with; independent of the object's name. */
  def name: String
}

object InteractionFormat {
  type State[F <: InteractionFormat] = F match { case Typed[s, g, a] => s }
  type Grading[F <: InteractionFormat] = F match { case Typed[s, g, a] => g }
  type Scaffolding[F <: InteractionFormat] = F match { case Typed[s, g, a] => a }

  /** Only format definitions use three parameters; consumers carry the format singleton. */
  sealed abstract class Typed[S: ReadWriter, G: ReadWriter, A: ReadWriter](val name: String,
      graderCodec: ReadWriter[WorkbookInteractionGrader[S, G]] = WorkbookInteractionGrader.readWriter[S, G]()) extends InteractionFormat {
    final type InteractionStateType = S
    final type GradingInfoType = G
    final type ScaffoldingInfoType = A
    final lazy val codecs: Codecs[this.type] = new Codecs[this.type](summon[ReadWriter[S]], summon[ReadWriter[G]], summon[ReadWriter[A]], graderCodec)
  }

  /** The singleton value determines F. Scala does not preserve that equality when widening
    * a method argument, so the invariant codec is narrowed once at this boundary.
    */
  def codecsFor[F <: InteractionFormat](format: F): Codecs[F] =
    format.codecs.asInstanceOf[Codecs[F]]

  inline def instance[F <: InteractionFormat](using value: ValueOf[F]): F = value.value

  final class Codecs[F <: InteractionFormat](
      givenState: ReadWriter[State[F]], givenGrading: ReadWriter[Grading[F]], givenScaffolding: ReadWriter[Scaffolding[F]], givenGrader: ReadWriter[WorkbookInteractionGrader[State[F], Grading[F]]]) {
    private given stateRW: ReadWriter[State[F]] = givenState
    private given gradingRW: ReadWriter[Grading[F]] = givenGrading
    private given scaffoldingRW: ReadWriter[Scaffolding[F]] = givenScaffolding
    private given graderRW: ReadWriter[WorkbookInteractionGrader[State[F], Grading[F]]] = givenGrader
    private given gradingResultRW: ReadWriter[GradingResult[State[F], Grading[F]]] = macroRW
    private given answerRW: ReadWriter[AnswerChangedEvent[F]] = macroRW
    private given gradingStartedRW: ReadWriter[GradingStartedEvent[F]] = macroRW
    private given gradingCompletedRW: ReadWriter[GradingCompletedEvent[F]] = macroRW
    private given scaffoldingStartedRW: ReadWriter[ScaffoldingStartedEvent[F]] = macroRW
    private given scaffoldingCompletedRW: ReadWriter[ScaffoldingCompletedEvent[F]] = macroRW
    private val taggedEventRW: ReadWriter[InteractionEvent[F]] = ReadWriter.merge(
      answerRW, gradingStartedRW, gradingCompletedRW, scaffoldingStartedRW, scaffoldingCompletedRW)
    // Never create a new identity while decoding a saved event with a missing ID; EventId validates the format.
    private given eventRW: ReadWriter[InteractionEvent[F]] = readwriter[ujson.Value].bimap[InteractionEvent[F]](
      event => writeJs(event)(using taggedEventRW),
      json => {
        require(json.obj.contains("eventId"), "Interaction event is missing its client-generated eventId")
        read[InteractionEvent[F]](json)(using taggedEventRW)
      })
    private given eventsRW: ReadWriter[InteractionEvents[F]] = macroRW
    given historyReadWriter: ReadWriter[InteractionVariableHistory[F]] = macroRW

    val stateSerializer: Serializer[State[F]] = Serializer.fromImplicitRW
    val gradingInfoSerializer: Serializer[Grading[F]] = Serializer.fromImplicitRW
    val scaffoldingInfoSerializer: Serializer[Scaffolding[F]] = Serializer.fromImplicitRW
    val eventSerializer: Serializer[InteractionEvent[F]] = Serializer.fromImplicitRW
    val historySerializer: Serializer[InteractionVariableHistory[F]] = Serializer.fromImplicitRW
  }

  private lazy val programmingGraders = WorkbookInteractionGrader.readWriter[ProgrammingState, AdditionalProgrammingTestInfo](
    RecreateShapeGrader.codec,
    ProgramStructureGrader.codec,
    TurtleCommandUsageGrader.codec,
    ProgrammingUnitTestGrader.codec)
  private lazy val textGraders = WorkbookInteractionGrader.readWriter[String, MessengerModel](
    TextInputLlmGrader.codec)
  private lazy val slideshowGraders = WorkbookInteractionGrader.readWriter[SlideshowState, Unit](
    SlideshowGrader.codec)

  case object TextInteractionFormat extends Typed[String, Unit, Unit]("TextInteractionFormat")
  case object LabeledCheckboxInteractionFormat extends Typed[Boolean, Unit, Unit]("LabeledCheckboxInteractionFormat")
  case object MessagingInteractionFormat extends Typed[MessengerModelScaffolding, Unit, Unit]("MessagingInteractionFormat")
  case object TextInputWithLlmGradingAndScaffoldingFormat extends Typed[String, MessengerModel, MessengerModel]("TextInputWithLlmGradingAndScaffoldingFormat", textGraders)
  case object ChoiceInteractionFormat extends Typed[ChoiceState, Unit, Unit]("ChoiceInteractionFormat", WorkbookInteractionGrader.readWriter(
    ChoiceGrader.codec))
  case object ProgrammingExerciseWithLlmScaffoldingAndGrading extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]("ProgrammingExerciseWithLlmScaffoldingAndGrading", programmingGraders)
  case object SqlCommandExerciseFormat extends Typed[String, Unit, Unit]("SqlCommandExerciseFormat")
  case object QrCodeInteractionFormat extends Typed[QrCode, Unit, Unit]("QrCodeInteractionFormat")
  case object BinaryPixelInteractionFormat extends Typed[BinaryPixelImage, Unit, Unit]("BinaryPixelInteractionFormat")
  case object TurtleProjectFormat extends Typed[TurtleStitchProjectState, Unit, Unit]("TurtleProjectFormat")
  case object CoordinatePlotInteractionFormat extends Typed[PlotAnswer, Unit, Unit]("CoordinatePlotInteractionFormat")
  case object CodeTaskToggleInteractionFormat extends Typed[CodeTaskToggleState, Unit, Unit]("CodeTaskToggleInteractionFormat")
  case object SketchDownloadInteractionFormat extends Typed[String, Unit, Unit]("SketchDownloadInteractionFormat")
  case object MailInteractionFormat extends Typed[InboxStateScaffolding, Unit, Unit]("MailInteractionFormat")
  case object MailEditorFormat extends Typed[InboxStateScaffolding, Unit, Unit]("MailEditorFormat")
  case object AnswerTableInteractionFormat extends Typed[TableAnswer, Unit, Unit]("AnswerTableInteractionFormat", WorkbookInteractionGrader.readWriter(
    TableCellGrader.codec))
  case object UnicodeComparisonInteractionFormat extends Typed[UnicodeComparisonAnswer, Unit, Unit]("UnicodeComparisonInteractionFormat") { val maxTextLength = 256 }
  case object BlockchainInteractionFormat extends Typed[TeachingChain, Unit, Unit]("BlockchainInteractionFormat")
  case object Sha256InteractionFormat extends Typed[Sha256Answer, Unit, Unit]("Sha256InteractionFormat")
  case object SquareMiddleHashInteractionFormat extends Typed[SquareMiddleHashAnswer, Unit, Unit]("SquareMiddleHashInteractionFormat")
  case object ReorderCodeInteractionFormat extends Typed[ReorderInteractionState[String], Unit, Unit]("ReorderCodeInteractionFormat", WorkbookInteractionGrader.readWriter(
    ReorderCodeGrader.codec))
  case object ReorderMapIdInteractionFormat extends Typed[ReorderInteractionState[LanguageMapContentId], Unit, Unit]("ReorderMapIdInteractionFormat", WorkbookInteractionGrader.readWriter(
    ReorderMapIdGrader.codec))
  case object MatchingInteractionFormat extends Typed[MatchingInteractionState, Int, Unit]("MatchingInteractionFormat", WorkbookInteractionGrader.readWriter(
    MatchingGrader.codec))
  case object LikertScaleInteractionFormat extends Typed[MatchingInteractionState, Unit, Unit]("LikertScaleInteractionFormat")
  case object SortingInteractionFormat extends Typed[SortingInteractionState, Unit, Unit]("SortingInteractionFormat")
  case object SortingReasonInteractionFormat extends Typed[SortingReasonInteractionState, Unit, Unit]("SortingReasonInteractionFormat")
  case object SlideshowFormat extends Typed[SlideshowState, Unit, Unit]("SlideshowFormat", slideshowGraders)
  case object EvacuationConstructFloorInteractionFormat extends Typed[EvacuationFloorPlan, Unit, Unit]("EvacuationConstructFloorInteractionFormat")
  case object ThresholdNeuronInteractionFormat extends Typed[NeuronParameters, Unit, Unit]("ThresholdNeuronInteractionFormat")
  case object CompressionExperimentInteractionFormat extends Typed[CompressionExperiment, Unit, Unit]("CompressionExperimentInteractionFormat")
  case object TurtleRecreateShapeInteractionFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]("TurtleRecreateShapeInteractionFormat", programmingGraders)
  case object ProgrammingExerciseFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]("ProgrammingExerciseFormat", programmingGraders)
  case object SortListExerciseFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]("SortListExerciseFormat", programmingGraders)
  case object EvacuationExperimentFormat extends Typed[EvacuationExperiment, Unit, Unit]("EvacuationExperimentFormat")

  lazy val knownFormats: Seq[InteractionFormat] = List(
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
    MatchingInteractionFormat,
    LikertScaleInteractionFormat,
    SortingInteractionFormat,
    SortingReasonInteractionFormat,
    SlideshowFormat,
    EvacuationConstructFloorInteractionFormat,
    ThresholdNeuronInteractionFormat,
    CompressionExperimentInteractionFormat,
    TurtleRecreateShapeInteractionFormat,
    ProgrammingExerciseFormat,
    SortListExerciseFormat,
    EvacuationExperimentFormat
  )

  def fromName(name: String): InteractionFormat =
    knownFormats.find(_.name == name).getOrElse(throw new IllegalArgumentException(s"Unknown interaction format '$name'"))

  given ReadWriter[InteractionFormat] = readwriter[String].bimap(_.name, fromName)
}
