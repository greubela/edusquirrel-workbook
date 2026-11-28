package it.evadid.homepage.workbook.htmlRenderer

import scala.util.control.NonFatal
import it.evadid.workbook.interactions.compression.CompressionExperimentInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.compression.CompressionExperimentRenderer
import it.evadid.workbook.interactions.evacuation.EvacuationSimulationInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation.EvacuationSimulationRenderer
import it.evadid.workbook.interactions.evacuation.EvacuationConstructFloorInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation.EvacuationConstructFloorRenderer
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.sqlEditor.HtmlSqlCommandExerciseRenderer
import it.evadid.workbook.interactions.sql.SqlCommandExercise
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.displayRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle.{HtmlCodeTaskToggleRenderer, HtmlSketchDownloadRenderer}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.matching.{HtmlLikertScaleRenderer, HtmlMatchingInteractionRenderer}
import it.evadid.workbook.interactions.matching.{LikertScaleInteraction, MatchingInteraction}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise.HtmlSortingInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingReasonExercise.HtmlSortingReasonInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{HtmlTurtleRecreateShapeRenderer, HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRendererLegacy}
import it.evadid.homepage.workbook.htmlRenderer.structureRenderer.*
import it.evadid.workbook.interactions.emailSimulator.{MailEditor, MailInteraction}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator.{HtmlMailEditorRenderer, HtmlMailInteractionRenderer}
import it.evadid.workbook.interactions.qr.CreateQrCodeInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.qr.CreateQrCodeInteractionRenderer
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.display.*
import it.evadid.workbook.interactions.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, TextInteraction}
import it.evadid.workbook.interactions.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.interactions.gpt.GptInteractionElement
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.TextInputWithScaffoldingAndGradingRenderer
import it.evadid.workbook.interactions.reorderExercise.ReorderInteraction
import it.evadid.workbook.interactions.slideshow.Slideshow
import it.evadid.workbook.elements.structure.{ExerciseContainer, ExerciseGroup, Workbook}
import org.scalajs.dom.HTMLDivElement
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.interactions.neuron.ThresholdNeuronInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice.WorkbookContentChoiceInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.neuron.ThresholdNeuronRenderer
import it.evadid.workbook.interactions.table.AnswerTableInteraction
import it.evadid.workbook.interactions.plot.CoordinatePlotInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.plot.CoordinatePlotRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.table.AnswerTableRenderer
import it.evadid.workbook.interactions.pixel.BinaryPixelInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.pixel.BinaryPixelRenderer
import it.evadid.workbook.interactions.blockchain.SquareMiddleHashInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.SquareMiddleHashRenderer
import it.evadid.workbook.interactions.blockchain.Sha256Interaction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.Sha256Renderer
import it.evadid.workbook.interactions.blockchain.BlockchainInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.BlockchainRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.slideshow.HtmlSlideshowEditor
import it.evadid.workbook.interactions.text.UnicodeComparisonInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.text.UnicodeComparisonRenderer
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava, SortListExercise}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps.HtmlSortListExerciseRenderer
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{TurtleRecreateShapeInteraction, TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteractionLegacy}
import it.evadid.workbook.interactions.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.interactions.sorting.sortingReasonExercise.SortingReasonInteraction

trait HtmlRenderFactory[T <: WorkbookElement] {

  def render(fullInfo: FullInfo, workbookElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = renderAppElement(fullInfo, workbookElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]

  def renderAppElement(fullInfo: FullInfo, workbookElement: T): HtmlWorkbookElement[T, HtmlAppElement]

  protected def laminarHelper(fullInfo: FullInfo): LaminarRenderHelper = LaminarRenderHelper(fullInfo)

  def placeholder(fullInfo: FullInfo, workbookElement: WorkbookElement, str: String): AtomarLineRendering = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div(s"${this.getClass.getName}::render cannot yet render an object because with the following information: $str!")
    AtomarLineRendering.basicLine(fullInfo, workbookElement, dom)
  }

}


object HtmlRenderFactory {


  trait LineBasedRenderingFactory[T <: WorkbookElement] extends HtmlRenderFactory[T] {
    override def renderAppElement(fullInfo: FullInfo, workbookElement: T): HtmlWorkbookElement[T, HtmlAppElement] =
      renderWorkbookElement(fullInfo, workbookElement).asInstanceOf[HtmlWorkbookElement[T, HtmlAppElement]]

    def renderWorkbookElement(fullInfo: FullInfo, workbookElement: T): HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = {
      HtmlWorkbookElement[WorkbookElement, AtomarLineRendering](workbookElement, createRendering(fullInfo, workbookElement))
    }

    protected def createRendering(fullInfo: FullInfo, workbookElement: T): AtomarLineRendering
  }


  private[workbook] def createPlaceholderElement[T <: WorkbookElement](fullInfo: FullInfo, workbookElement: T, msg: String): HtmlWorkbookElement[T, AtomarLineRendering] = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div(msg)
    val rl: AtomarLineRendering = AtomarLineRendering.basicLine(fullInfo, workbookElement, dom)
    HtmlWorkbookElement[T, AtomarLineRendering](workbookElement, rl)
  }


  /** Renders any element; renderer failures become a visible placeholder instead of breaking the page. */
  def render[T <: WorkbookElement](fullInfo: FullInfo, anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] =
    try anyElement match {
      case c: ExerciseContainer => HtmlExerciseContainerRenderer.render(fullInfo, c)
      case other => renderWorkbookElement(fullInfo, other).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    } catch case NonFatal(e) =>
      createPlaceholderElement(fullInfo, anyElement, s"Cannot render Element ${anyElement.getClass.getSimpleName} because of exception: ${e.getMessage}")
        .asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]

  def renderWorkbookElement[T <: WorkbookElement](fullInfo: FullInfo, anyElement: T): HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = {
    anyElement match {
      case p: TwoColumnPanel => HtmlTwoColumnPanelRenderer.renderWorkbookElement(fullInfo, p)
      case g: ExerciseGroup => HtmlExerciseGroupRenderer.renderWorkbookElement(fullInfo, g)
      case c: DisplayLangMapContent => HtmlDisplayLangMapContentRenderer.renderWorkbookElement(fullInfo, c)
      case b: LabeledWorkbookElement[?] => HtmlLabeledWorkbookElementRenderer(b).renderWorkbookElement(fullInfo, b)
      case c: CollapsibleInstructionElement => HtmlCollapsibleInstructionRenderer.renderWorkbookElement(fullInfo, c)
      case s: WorkbookSubmissionElement => HtmlWorkbookSubmissionRenderer.renderWorkbookElement(fullInfo, s)
      case i: WorkbookImageElement => HtmlProxyAppElementRenderer.renderWorkbookElement(fullInfo, i, HtmlImageElement(fullInfo, i))

      // interactions
      case e: CompressionExperimentInteraction => CompressionExperimentRenderer.renderWorkbookElement(fullInfo, e)
      case h: SquareMiddleHashInteraction => SquareMiddleHashRenderer.renderWorkbookElement(fullInfo, h)
      case h: Sha256Interaction => Sha256Renderer.renderWorkbookElement(fullInfo, h)
      case h: BlockchainInteraction => BlockchainRenderer.renderWorkbookElement(fullInfo, h)
      case e: EvacuationSimulationInteraction => EvacuationSimulationRenderer.renderWorkbookElement(fullInfo, e)
      case e: EvacuationConstructFloorInteraction => EvacuationConstructFloorRenderer.renderWorkbookElement(fullInfo, e)
      case h: UnicodeComparisonInteraction => UnicodeComparisonRenderer.renderWorkbookElement(fullInfo, h)
      case p: BinaryPixelInteraction => BinaryPixelRenderer.renderWorkbookElement(fullInfo, p)
      case t: AnswerTableInteraction => AnswerTableRenderer.renderWorkbookElement(fullInfo, t)
      case p: CoordinatePlotInteraction => CoordinatePlotRenderer.renderWorkbookElement(fullInfo, p)
      case c: WorkbookContentChoiceInteraction => WorkbookContentChoiceInteractionRenderer.renderWorkbookElement(fullInfo, c)
      case n: ThresholdNeuronInteraction => ThresholdNeuronRenderer.renderWorkbookElement(fullInfo, n)
      case m: MailInteraction => HtmlMailInteractionRenderer.renderWorkbookElement(fullInfo, m)
      case m: MailEditor => HtmlMailEditorRenderer.renderWorkbookElement(fullInfo, m)
      case q: CreateQrCodeInteraction => CreateQrCodeInteractionRenderer.renderWorkbookElement(fullInfo, q)
      case i: TextInteraction => HtmlSimpleTextInteractionRenderer.renderWorkbookElement(fullInfo, i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.renderWorkbookElement(fullInfo, i)
      case i: LabeledNumberInteraction => HtmlBasicNumberRenderer.renderWorkbookElement(fullInfo, i)
      case s: SortingInteraction => HtmlSortingInteractionRenderer.renderWorkbookElement(fullInfo, s)
      case s: SortingReasonInteraction => HtmlSortingReasonInteractionRenderer.renderWorkbookElement(fullInfo, s)
      case r: ReorderInteraction.ReorderCodeInteraction => HtmlReorderInteractionRenderer.renderWorkbookElement(fullInfo, r)
      case r: ReorderInteraction.ReorderMapIdInteraction => HtmlReorderInteractionRenderer.renderWorkbookElement(fullInfo, r)
      case c: CodeTaskToggleInteraction => HtmlCodeTaskToggleRenderer.renderWorkbookElement(fullInfo, c)
      case d: SketchDownloadInteraction => HtmlSketchDownloadRenderer.renderWorkbookElement(fullInfo, d)
      case i: MatchingInteraction => HtmlMatchingInteractionRenderer.renderWorkbookElement(fullInfo, i)
      case i: LikertScaleInteraction => HtmlLikertScaleRenderer.renderWorkbookElement(fullInfo, i)
      // plugins -- turtle
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.renderWorkbookElement(fullInfo, t)
      case t: TurtleRecreateShapeInteraction => HtmlTurtleRecreateShapeRenderer.renderWorkbookElement(fullInfo, t)
      case t: TurtleStitchRecreateShapeInteractionLegacy => HtmlTurtleStitchRecreateShapeRendererLegacy.renderWorkbookElement(fullInfo, t)
      // plugins -- gpt
      case g: GptInteractionElement => HtmlGptTextfieldInteractionRenderer.renderWorkbookElement(fullInfo, g)
      case t: TextInputWithScaffoldingAndGrading => TextInputWithScaffoldingAndGradingRenderer.renderWorkbookElement(fullInfo, t)
      // plugins -- slideshow & reorder
      case s: Slideshow => HtmlSlideshowEditor.renderWorkbookElement(fullInfo, s)
      case s: SqlCommandExercise => HtmlSqlCommandExerciseRenderer.renderWorkbookElement(fullInfo, s)
      case p: ProgrammingExercise => HtmlProgrammingExerciseRenderer.renderWorkbookElement(fullInfo, p)
      case p: ProgrammingExerciseFullJava => HtmlProgrammingExerciseFullJavaRenderer.renderWorkbookElement(fullInfo, p)
      case s: SortListExercise => HtmlSortListExerciseRenderer.renderWorkbookElement(fullInfo, s)
      case other => createPlaceholderElement(fullInfo, other, "HtmlRenderFactory::renderWorkbookElement cannot yet render objects of type '" + other.getClass.getName + "'!")
    }
  }

}
