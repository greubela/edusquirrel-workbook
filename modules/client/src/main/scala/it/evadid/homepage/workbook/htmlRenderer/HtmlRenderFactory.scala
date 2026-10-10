package it.evadid.homepage.workbook.htmlRenderer

import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.compression.CompressionExperimentRenderer
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation.EvacuationSimulationRenderer
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation.EvacuationConstructFloorRenderer
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.sqlEditor.HtmlSqlCommandExerciseRenderer
import it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.displayRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle.{HtmlCodeTaskToggleRenderer, HtmlSketchDownloadRenderer}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise.HtmlSortingInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingReasonExercise.HtmlSortingReasonInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{HtmlTurtleRecreateShapeRenderer, HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRendererLegacy}
import it.evadid.homepage.workbook.htmlRenderer.structureRenderer.*
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailEditor, MailInteraction}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator.{HtmlMailEditorRenderer, HtmlMailInteractionRenderer}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.qr.CreateQrCodeInteractionRenderer
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.*
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{PanelTwoColumnImage, Slideshow}
import it.evadid.workbook.elements.structureElements.{ExerciseContainer, Workbook}
import org.scalajs.dom.HTMLDivElement
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.ThresholdNeuronInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice.ChoiceInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.neuron.ThresholdNeuronRenderer
import it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction
import it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.plot.CoordinatePlotRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.table.AnswerTableRenderer
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.pixel.BinaryPixelRenderer
import it.evadid.workbook.elements.interactionElements.blockchain.SquareMiddleHashInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.SquareMiddleHashRenderer
import it.evadid.workbook.elements.interactionElements.blockchain.Sha256Interaction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.Sha256Renderer
import it.evadid.workbook.elements.interactionElements.blockchain.BlockchainInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain.BlockchainRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.slideshow.HtmlSlideshowEditor
import it.evadid.workbook.elements.interactionElements.text.UnicodeComparisonInteraction
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.text.UnicodeComparisonRenderer
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleRecreateShapeInteraction, TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteractionLegacy}
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.SortingReasonInteraction

trait HtmlRenderFactory[T <: WorkbookElement] {

  protected def fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo

  def render(workbookElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = renderAppElement(workbookElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]

  def renderAppElement(workbookElement: T): HtmlWorkbookElement[T, HtmlAppElement]

  protected val laminarHelper: LaminarRenderHelper = LaminarRenderHelper.singleton

  def placeholder(workbookElement: WorkbookElement, str: String): AtomarLineRendering = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div(s"${this.getClass.getName}::render cannot yet render an object because with the following information: $str!")
    AtomarLineRendering.basicLine(workbookElement, dom)
  }

}


object HtmlRenderFactory {


  trait LineBasedRenderingFactory[T <: WorkbookElement] extends HtmlRenderFactory[T] {
    override def renderAppElement(workbookElement: T): HtmlWorkbookElement[T, HtmlAppElement] =
      renderWorkbookElement(workbookElement).asInstanceOf[HtmlWorkbookElement[T, HtmlAppElement]]

    def renderWorkbookElement(workbookElement: T): HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = {
      HtmlWorkbookElement[WorkbookElement, AtomarLineRendering](workbookElement, createRendering(workbookElement))
    }

    protected def createRendering(workbookElement: T): AtomarLineRendering
  }


  private[workbook] def createPlaceholderElement[T <: WorkbookElement](workbookElement: T, msg: String): HtmlWorkbookElement[T, AtomarLineRendering] = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div(msg)
    val rl: AtomarLineRendering = AtomarLineRendering.basicLine(workbookElement, dom)
    HtmlWorkbookElement[T, AtomarLineRendering](workbookElement, rl)
  }


  def render[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = {
    try {
      renderStructureElement(anyElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    } catch case e: Throwable => try {
      renderWorkbookElement(anyElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    }
    catch case e: Throwable => {
      createPlaceholderElement(anyElement, s"Cannot render Element ${anyElement.getClass.getSimpleName} because of exception: ${e.getMessage}").asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    }
  }


  private def renderStructureElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = {
    anyElement.match {
      // structure
      case c: ExerciseContainer => HtmlExerciseContainerRenderer.render(c)
      case _: T => ???
    }
  }

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = {
    anyElement match {
      case c: DisplayLangMapContent => HtmlDisplayLangMapContentRenderer.renderWorkbookElement(c)
      case b: LabeledWorkbookElement[?] => HtmlLabeledWorkbookElementRenderer(b).renderWorkbookElement(b)
      case c: CollapsibleInstructionElement => HtmlCollapsibleInstructionRenderer.renderWorkbookElement(c)
      case i: WorkbookImageElement => HtmlProxyAppElementRenderer.renderWorkbookElement(i, HtmlImageElement(i))

      // interactions
      case e: CompressionExperimentInteraction => CompressionExperimentRenderer.renderWorkbookElement(e)
      case h: SquareMiddleHashInteraction => SquareMiddleHashRenderer.renderWorkbookElement(h)
      case h: Sha256Interaction => Sha256Renderer.renderWorkbookElement(h)
      case h: BlockchainInteraction => BlockchainRenderer.renderWorkbookElement(h)
      case e: EvacuationSimulationInteraction => EvacuationSimulationRenderer.renderWorkbookElement(e)
      case e: EvacuationConstructFloorInteraction => EvacuationConstructFloorRenderer.renderWorkbookElement(e)
      case h: UnicodeComparisonInteraction => UnicodeComparisonRenderer.renderWorkbookElement(h)
      case p: BinaryPixelInteraction => BinaryPixelRenderer.renderWorkbookElement(p)
      case t: AnswerTableInteraction => AnswerTableRenderer.renderWorkbookElement(t)
      case p: CoordinatePlotInteraction => CoordinatePlotRenderer.renderWorkbookElement(p)
      case c: ChoiceInteraction => ChoiceInteractionRenderer.renderWorkbookElement(c)
      case n: ThresholdNeuronInteraction => ThresholdNeuronRenderer.renderWorkbookElement(n)
      case m: MailInteraction => HtmlMailInteractionRenderer.renderWorkbookElement(m)
      case m: MailEditor => HtmlMailEditorRenderer.renderWorkbookElement(m)
      case q: CreateQrCodeInteraction => CreateQrCodeInteractionRenderer.renderWorkbookElement(q)
      case i: TextInteraction => HtmlSimpleTextInteractionRenderer.renderWorkbookElement(i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.renderWorkbookElement(i)
      case i: LabeledNumberInteraction => HtmlBasicNumberRenderer.renderWorkbookElement(i)
      case s: SortingInteraction => HtmlSortingInteractionRenderer.renderWorkbookElement(s)
      case s: SortingReasonInteraction => HtmlSortingReasonInteractionRenderer.renderWorkbookElement(s)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.renderWorkbookElement(r)
      case c: CodeTaskToggleInteraction => HtmlCodeTaskToggleRenderer.renderWorkbookElement(c)
      case d: SketchDownloadInteraction => HtmlSketchDownloadRenderer.renderWorkbookElement(d)
      /*case i: ChoiceSelectionInteraction => HtmlChoiceSelectionRenderer.renderWorkbookElement(i)
      case i: MatchingInteraction => HtmlMatchingInteractionRenderer.renderWorkbookElement(i)
      case i: CategorizationInteraction => HtmlCategorizationInteractionRenderer.renderWorkbookElement(i)
      case i: FillInBlanksInteraction => HtmlFillInBlanksRenderer.renderWorkbookElement(i)
      case i: DropdownBlanksInteraction => HtmlDropdownBlanksRenderer.renderWorkbookElement(i)
      case i: TableFillInInteraction => HtmlTableFillInRenderer.renderWorkbookElement(i)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.renderWorkbookElement(r)*/
      // plugins -- turtle
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.renderWorkbookElement(t)
      case t: TurtleRecreateShapeInteraction => HtmlTurtleRecreateShapeRenderer.renderWorkbookElement(t)
      case t: TurtleStitchRecreateShapeInteractionLegacy => HtmlTurtleStitchRecreateShapeRendererLegacy.renderWorkbookElement(t)
      // plugins -- gpt
      case g: GptInteractionElement => HtmlGptTextfieldInteractionRenderer.renderWorkbookElement(g)
      // plugins -- slideshow & reorder
      case s: Slideshow => HtmlSlideshowEditor.renderWorkbookElement(s)
      /*case r: HtmlReorderInteraction[?] @unchecked => fromElement(r, r.getDomElement())*/
      // case e: HtmlEmbeddedDomInteraction => fromAppElement(e, e.domElement)
      case s: SqlCommandExercise => HtmlSqlCommandExerciseRenderer.renderWorkbookElement(s)
      case p: ProgrammingExercise => HtmlProgrammingExerciseRenderer.renderWorkbookElement(p)
      case p: ProgrammingExerciseFullJava => HtmlProgrammingExerciseFullJavaRenderer.renderWorkbookElement(p)
      case a: T => createPlaceholderElement(a, "HtmlRenderFactory::renderWorkbookElement cannot yet render objects of type '" + a.getClass.getName + "'!")
      // error
    }
  }

}
