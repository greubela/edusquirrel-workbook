package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.displayRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise.HtmlSortingInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingReasonExercise.HtmlSortingReasonInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRenderer}
import it.evadid.homepage.workbook.htmlRenderer.structureRenderer.{HtmlExerciseContainerRenderer, HtmlWorkbookRenderer}
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.*
import it.evadid.workbook.elements.interactionElements.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.elements.structureElements.{ExerciseContainer, Workbook}
import org.scalajs.dom.HTMLDivElement

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
      HtmlWorkbookElement[WorkbookElement, AtomarLineRendering](fullInfo, workbookElement, createRendering(workbookElement))
    }

    protected def createRendering(workbookElement: T): AtomarLineRendering
  }

  private def createPlaceholderElement[T <: WorkbookElement](workbookElement: T): HtmlWorkbookElement[T, AtomarLineRendering] = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div("HtmlRenderFactory::renderWorkbookElement cannot yet render objects of type '" + workbookElement.getClass.getName + "'!")
    val rl: AtomarLineRendering = AtomarLineRendering.basicLine(workbookElement, dom)
    HtmlWorkbookElement[T, AtomarLineRendering](HtmlFullWorkbookApp.fullInfo, workbookElement, rl)

  }


  def renderWorkbook(workbook: Workbook): HtmlAppElement = {
    HtmlWorkbookRenderer.renderAppElement(workbook)
  }

  def render[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = {
    try {
      renderStructureElement(anyElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    } catch case e: Throwable => {
      renderWorkbookElement(anyElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    }
  }


  private def renderStructureElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = {
    anyElement.match {
      // structure
      case w: Workbook => HtmlWorkbookRenderer.render(w)
      case c: ExerciseContainer => HtmlExerciseContainerRenderer.render(c)
      case _: T => ???
    }
  }

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = {
    anyElement match {
      case c: DisplayLangMapContent => HtmlDisplayLangMapContentRenderer.renderWorkbookElement(c)
      case b: LabeledWorkbookElement[?] => HtmlLabeledWorkbookElementRenderer(b).renderWorkbookElement(b)
      case c: CollapsibleInstructionElement => HtmlCollapsibleInstructionRenderer.renderWorkbookElement(c)
      case i: ImageElement => HtmlProxyAppElementRenderer.renderWorkbookElement(i, HtmlImageElement(i))

      // interactions
      case i: TextInteraction => HtmlSimpleTextInteractionRenderer.renderWorkbookElement(i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.renderWorkbookElement(i)
      case i: LabeledNumberInteraction => HtmlBasicNumberRenderer.renderWorkbookElement(i)
      case s: SortingInteraction => HtmlSortingInteractionRenderer.renderWorkbookElement(s)
      case s: SortingReasonInteraction => HtmlSortingReasonInteractionRenderer.renderWorkbookElement(s)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.renderWorkbookElement(r)
      /*case i: ChoiceSelectionInteraction => HtmlChoiceSelectionRenderer.renderWorkbookElement(i)
      case i: MatchingInteraction => HtmlMatchingInteractionRenderer.renderWorkbookElement(i)
      case i: CategorizationInteraction => HtmlCategorizationInteractionRenderer.renderWorkbookElement(i)
      case i: FillInBlanksInteraction => HtmlFillInBlanksRenderer.renderWorkbookElement(i)
      case i: DropdownBlanksInteraction => HtmlDropdownBlanksRenderer.renderWorkbookElement(i)
      case i: TableFillInInteraction => HtmlTableFillInRenderer.renderWorkbookElement(i)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.renderWorkbookElement(r)*/
      // plugins -- turtle
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.renderWorkbookElement(t)
      case t: TurtleStitchRecreateShapeInteraction => HtmlTurtleStitchRecreateShapeRenderer.renderWorkbookElement(t)
      // plugins -- gpt
      case g: GptInteractionElement => HtmlGptTextfieldInteractionRenderer.renderWorkbookElement(g)
      // plugins -- slideshow & reorder
      case s: Slideshow => HtmlSlideshowEditor.renderWorkbookElement(s) // editor instead of renderer
      /*case r: HtmlReorderInteraction[?] @unchecked => fromElement(r, r.getDomElement())*/
      // case e: HtmlEmbeddedDomInteraction => fromAppElement(e, e.domElement)
      case p: ProgrammingExercise => HtmlProgrammingExerciseRenderer.renderWorkbookElement(p)
      case a: T => createPlaceholderElement(a)
      // error
    }
  }

}
