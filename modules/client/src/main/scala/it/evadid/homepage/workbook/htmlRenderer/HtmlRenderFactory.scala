package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingLine}
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.interactionEditors.*
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.gpt.HtmlGptTextfieldInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch.{HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRenderer}
import it.evadid.homepage.workbook.htmlRenderer.structureRenderer.{HtmlExerciseContainerRenderer, HtmlWorkbookRenderer}
import it.evadid.util.logging.Logger
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import it.evadid.workbook.model.interaction.basic.*
import it.evadid.workbook.model.interaction.plugins.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.model.interaction.plugins.gpt.GptInteractionElement
import it.evadid.workbook.model.interaction.plugins.reorderExercise.ReorderInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.Slideshow
import org.scalajs.dom.HTMLDivElement

trait HtmlRenderFactory[T <: WorkbookElement] {

  protected def fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo

  protected def uiAndDomLogger: Logger = fullInfo.loggerSystemInfo.uiAndDomLogger

  def render(workbookElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = renderAppElement(workbookElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]

  def renderAppElement(workbookElement: T): HtmlWorkbookElement[T, HtmlAppElement]

  def contentIdStringSignal(contentId: LanguageMapContentId): Signal[String] = {
    HtmlRenderFactory.contentIdStringSignal(contentId)
  }

  def placeholder(str: String): AtomarLineRendering = {
    val dom: ReactiveHtmlElement[HTMLDivElement] = div(s"${this.getClass.getName}::render cannot yet render an object because with the following information: $str!")
    RenderingLine(false, dom)
  }

}


object HtmlRenderFactory {

  def contentIdStringSignal(languageMapContentId: LanguageMapContentId): Signal[String] = {
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(languageMapContentId)
  }

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
    val rl: AtomarLineRendering = RenderingLine(false, dom, "")
    HtmlWorkbookElement[T, AtomarLineRendering](HtmlFullWorkbookApp.fullInfo, workbookElement, rl)

  }


  def renderWorkbook(workbook: Workbook): HtmlAppElement = {
    HtmlWorkbookRenderer.renderAppElement(workbook)
  }

  def render[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[WorkbookElement, HtmlAppElement] = {
    try {
      renderStructureElement(anyElement).asInstanceOf[HtmlWorkbookElement[WorkbookElement, HtmlAppElement]]
    } catch case e: Exception => {
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
      case c: LangMapContentBasedElement => HtmlLangMapContentRenderer.renderWorkbookElement(c)
      case b: LabeledWorkbookElement[?] => HtmlLabeledWorkbookElementRenderer(b).renderWorkbookElement(b)
      case i: ImageElement => HtmlProxyAppElementRenderer.renderWorkbookElement(i, HtmlImageElement(i))

      // interactions
      case i: TextInteractionBasic => HtmlSimpleTextInteractionRenderer.renderWorkbookElement(i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.renderWorkbookElement(i)
      case i: LabeledNumberInteraction => HtmlBasicNumberRenderer.renderWorkbookElement(i)
      /*case i: ChoiceSelectionInteraction => HtmlChoiceSelectionRenderer.renderWorkbookElement(i)
      case i: MatchingInteraction => HtmlMatchingInteractionRenderer.renderWorkbookElement(i)
      case i: CategorizationInteraction => HtmlCategorizationInteractionRenderer.renderWorkbookElement(i)
      case i: FillInBlanksInteraction => HtmlFillInBlanksRenderer.renderWorkbookElement(i)
      case i: DropdownBlanksInteraction => HtmlDropdownBlanksRenderer.renderWorkbookElement(i)
      case i: TableFillInInteraction => HtmlTableFillInRenderer.renderWorkbookElement(i)*/
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.renderWorkbookElement(r)
      // plugins -- turtle
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.renderWorkbookElement(t)
      case t: TurtleStitchRecreateShapeInteraction => HtmlTurtleStitchRecreateShapeRenderer.renderWorkbookElement(t)
      // plugins -- gpt
      case g: GptInteractionElement => HtmlGptTextfieldInteractionRenderer.renderWorkbookElement(g)
      // plugins -- slideshow & reorder
      case s: Slideshow => HtmlSlideshowEditor.renderWorkbookElement(s) // editor instead of renderer
      /*case r: HtmlReorderInteraction[?] @unchecked => fromElement(r, r.getDomElement())*/
      // case e: HtmlEmbeddedDomInteraction => fromAppElement(e, e.domElement)

      case a: T => ??? //createPlaceholderElement(a)
      // error
    }
  }

}




