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
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.interactions.blockchain.TeachingChain
import it.evadid.workbook.interactions.compression.CompressionExperiment
import it.evadid.workbook.interactions.evacuation.{EvacuationExperiment, EvacuationFloorPlan}
import it.evadid.workbook.interactions.pixel.BinaryPixelImage
import it.evadid.workbook.interactions.plot.PlotAnswer
import it.evadid.workbook.interactions.qr.QrCode
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.ChoiceState
import it.evadid.workbook.interactions.matching.MatchingInteractionState
import it.evadid.core.util.crypto.ContentIdentity
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
  final def historySerializer: Serializer[InteractionHistory[this.type]] = codecs.historySerializer
  /** The stable name histories are tagged with; independent of the object's name. */
  def name: String
}

object InteractionFormat {
  type State[F <: InteractionFormat] = F match { case Typed[s, g, a] => s }
  type Grading[F <: InteractionFormat] = F match { case Typed[s, g, a] => g }
  type Scaffolding[F <: InteractionFormat] = F match { case Typed[s, g, a] => a }

  /** Only format definitions use three parameters; consumers carry the format singleton. */
  /** `stateFingerprint` names an answer for feedback references; by default the canonical JSON of its codec. */
  sealed abstract class Typed[S: ReadWriter, G: ReadWriter, A: ReadWriter](val name: String,
      stateFingerprint: Option[S => String] = None) extends InteractionFormat {
    final type InteractionStateType = S
    final type GradingInfoType = G
    final type ScaffoldingInfoType = A
    private def reference(state: S): StateReference = StateReference(stateFingerprint.fold(
      ContentIdentity.key(writeJs(state)))(fingerprint => ContentIdentity.key(ujson.Str(fingerprint(state)))))
    final lazy val codecs: Codecs[this.type] =
      new Codecs[this.type](this, summon[ReadWriter[S]], summon[ReadWriter[G]], summon[ReadWriter[A]], reference)
  }

  /** The singleton value determines F. Scala does not preserve that equality when widening
    * a method argument, so the invariant codec is narrowed once at this boundary.
    */
  def codecsFor[F <: InteractionFormat](format: F): Codecs[F] =
    format.codecs.asInstanceOf[Codecs[F]]

  inline def instance[F <: InteractionFormat](using value: ValueOf[F]): F = value.value

  /** The reference saved feedback uses for `state` of `format`. */
  def referenceOf[F <: InteractionFormat](format: F, state: State[F]): StateReference = codecsFor(format).fingerprint(state)

  final class Codecs[F <: InteractionFormat](format: F,
      givenState: ReadWriter[State[F]], givenGrading: ReadWriter[Grading[F]], givenScaffolding: ReadWriter[Scaffolding[F]],
      val fingerprint: State[F] => StateReference) {
    private given stateRW: ReadWriter[State[F]] = givenState
    private given gradingRW: ReadWriter[Grading[F]] = givenGrading
    private given scaffoldingRW: ReadWriter[Scaffolding[F]] = givenScaffolding
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
    /** A saved history holds only its events; the format is the envelope's. */
    given historyReadWriter: ReadWriter[InteractionHistory[F]] = readwriter[ujson.Value].bimap[InteractionHistory[F]](
      history => ujson.Obj("events" -> writeJs(history.events)),
      json => InteractionHistory(format, read[InteractionEvents[F]](json("events"))))

    val stateSerializer: Serializer[State[F]] = Serializer.fromImplicitRW
    val gradingInfoSerializer: Serializer[Grading[F]] = Serializer.fromImplicitRW
    val scaffoldingInfoSerializer: Serializer[Scaffolding[F]] = Serializer.fromImplicitRW
    val eventSerializer: Serializer[InteractionEvent[F]] = Serializer.fromImplicitRW
    val historySerializer: Serializer[InteractionHistory[F]] = Serializer.fromImplicitRW
  }

  /** Programs are referenced by their source text. */
  private val programFingerprint: Option[ProgrammingState => String] = Some(_.fingerprint())

  case object TextInteractionFormat extends Typed[String, Unit, Unit]("TextInteractionFormat")
  case object LabeledCheckboxInteractionFormat extends Typed[Boolean, Unit, Unit]("LabeledCheckboxInteractionFormat")
  case object MessagingInteractionFormat extends Typed[MessengerModelScaffolding, Unit, Unit]("MessagingInteractionFormat")
  case object TextInputWithLlmGradingAndScaffoldingFormat extends Typed[String, MessengerModel, MessengerModel]("TextInputWithLlmGradingAndScaffoldingFormat")
  case object ChoiceInteractionFormat extends Typed[ChoiceState, Unit, Unit]("ChoiceInteractionFormat")
  case object ProgrammingExerciseWithLlmScaffoldingAndGrading extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]("ProgrammingExerciseWithLlmScaffoldingAndGrading", programFingerprint)
  case object SqlCommandExerciseFormat extends Typed[String, Unit, Unit]("SqlCommandExerciseFormat")
  case object QrCodeInteractionFormat extends Typed[QrCode, Unit, Unit]("QrCodeInteractionFormat")
  case object BinaryPixelInteractionFormat extends Typed[BinaryPixelImage, Unit, Unit]("BinaryPixelInteractionFormat")
  case object TurtleProjectFormat extends Typed[TurtleStitchProjectState, Unit, Unit]("TurtleProjectFormat")
  case object CoordinatePlotInteractionFormat extends Typed[PlotAnswer, Unit, Unit]("CoordinatePlotInteractionFormat")
  case object CodeTaskToggleInteractionFormat extends Typed[CodeTaskToggleState, Unit, Unit]("CodeTaskToggleInteractionFormat")
  case object SketchDownloadInteractionFormat extends Typed[String, Unit, Unit]("SketchDownloadInteractionFormat")
  case object MailInteractionFormat extends Typed[InboxStateScaffolding, Unit, Unit]("MailInteractionFormat")
  case object MailEditorFormat extends Typed[InboxStateScaffolding, Unit, Unit]("MailEditorFormat")
  case object AnswerTableInteractionFormat extends Typed[TableAnswer, Unit, Unit]("AnswerTableInteractionFormat")
  case object UnicodeComparisonInteractionFormat extends Typed[UnicodeComparisonAnswer, Unit, Unit]("UnicodeComparisonInteractionFormat") { val maxTextLength = 256 }
  case object BlockchainInteractionFormat extends Typed[TeachingChain, Unit, Unit]("BlockchainInteractionFormat")
  case object Sha256InteractionFormat extends Typed[Sha256Answer, Unit, Unit]("Sha256InteractionFormat")
  case object SquareMiddleHashInteractionFormat extends Typed[SquareMiddleHashAnswer, Unit, Unit]("SquareMiddleHashInteractionFormat")
  case object ReorderCodeInteractionFormat extends Typed[ReorderInteractionState[String], Unit, Unit]("ReorderCodeInteractionFormat")
  case object ReorderMapIdInteractionFormat extends Typed[ReorderInteractionState[LanguageMapContentId], Unit, Unit]("ReorderMapIdInteractionFormat")
  case object MatchingInteractionFormat extends Typed[MatchingInteractionState, Int, Unit]("MatchingInteractionFormat")
  case object LikertScaleInteractionFormat extends Typed[MatchingInteractionState, Unit, Unit]("LikertScaleInteractionFormat")
  case object SortingInteractionFormat extends Typed[SortingInteractionState, Unit, Unit]("SortingInteractionFormat")
  case object SortingReasonInteractionFormat extends Typed[SortingReasonInteractionState, Unit, Unit]("SortingReasonInteractionFormat")
  case object SlideshowFormat extends Typed[SlideshowState, Unit, Unit]("SlideshowFormat")
  case object EvacuationConstructFloorInteractionFormat extends Typed[EvacuationFloorPlan, Unit, Unit]("EvacuationConstructFloorInteractionFormat")
  case object ThresholdNeuronInteractionFormat extends Typed[NeuronParameters, Unit, Unit]("ThresholdNeuronInteractionFormat")
  case object CompressionExperimentInteractionFormat extends Typed[CompressionExperiment, Unit, Unit]("CompressionExperimentInteractionFormat")
  case object TurtleRecreateShapeInteractionFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]("TurtleRecreateShapeInteractionFormat", programFingerprint)
  case object ProgrammingExerciseFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]("ProgrammingExerciseFormat", programFingerprint)
  case object SortListExerciseFormat extends Typed[ProgrammingState, AdditionalProgrammingTestInfo, Unit]("SortListExerciseFormat", programFingerprint)
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
