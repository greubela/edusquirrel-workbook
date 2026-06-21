package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.interactionEditors.*
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.gpt.HtmlGptTextfieldInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.slideshow.HtmlSlideshowRenderer
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch.{HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRenderer}
import it.evadid.homepage.workbook.legacy.htmlElements.HtmlEmbeddedDomInteraction
import it.evadid.homepage.workbook.legacy.htmlElements.interactions.HtmlReorderInteraction
import it.evadid.workbook.model.interaction.plugins.reorderExercise.ReorderInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.Slideshow
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.ImageElement.FileBasedImageElement
import it.evadid.workbook.model.elements.{ExerciseContainer, ImageElement, LabeledInstructionElement, LangMapContentBasedElement, Workbook}
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import it.evadid.workbook.model.interaction.basic.*
import it.evadid.workbook.model.interaction.plugins.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.model.interaction.plugins.gpt.GptInteractionElement
import it.evadid.workbook.model.interaction.plugins.reorderExercise.ReorderInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.Slideshow

trait HtmlRenderFactory[T <: WorkbookElement] {

  protected def fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo

  protected def createDomElement(workbookElement: T): Element

  def render(workbookElement: T): HtmlWorkbookElement[T] = HtmlWorkbookElement[T](fullInfo, workbookElement, createDomElement(workbookElement))

  def contentIdStringSignal(contentId: LanguageMapContentId): Signal[String] = {
    HtmlRenderFactory.contentIdStringSignal(contentId)
  }

  def placeholder(str: String): Element = div(s"${this.getClass.getName}::render cannot yet render an object because with the following information: $str!")

}

object HtmlRenderFactory {

  def contentIdStringSignal(contentId: LanguageMapContentId): Signal[String] = {
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(contentId)
  }

  implicit class HtmlDefaultRenderingOfWorkbookElement[T <: WorkbookElement](workbookElement: T) {
    def defaultRendering: HtmlWorkbookElement[?] = renderWorkbookElement(workbookElement)
  }

  private def createPlaceholderElement[T <: WorkbookElement](workbookElement: T): Element = {
    div("HtmlRenderFactory::renderWorkbookElement cannot yet render objects of type '" + workbookElement.getClass.getName + "'!")
  }

  private def fromElement[T <: WorkbookElement](any: T, element: Element): HtmlWorkbookElement[T] = {
    val useDom = L.div(L.cls := "workbook-element exercise-instruction", element)
    HtmlWorkbookElement[T](HtmlFullWorkbookApp.fullInfo, any, useDom)
  }

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[?] = {
    anyElement match {
      // structure
      case w: Workbook => HtmlWorkbookRenderer.render(w)
      case c: ExerciseContainer => HtmlExerciseContainerRenderer.render(c)
      // basic
      case c: LangMapContentBasedElement => HtmlLangMapContentRenderer.render(c)
      case i: ImageElement => fromElement[ImageElement](i, HtmlImageElement(i).getDomElement())
      case b: LabeledInstructionElement => HtmlInstructionLabeledPairRenderer.render(b)
      // interactions
      case i: TextInteractionBasic => HtmlSimpleTextInteractionRenderer.render(i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.render(i)
      case i: LabeledNumberInteraction => HtmlBasicNumberRenderer.render(i)
      case i: ChoiceSelectionInteraction => HtmlChoiceSelectionRenderer.render(i)
      case i: MatchingInteraction => HtmlMatchingInteractionRenderer.render(i)
      case i: CategorizationInteraction => HtmlCategorizationInteractionRenderer.render(i)
      case i: FillInBlanksInteraction => HtmlFillInBlanksRenderer.render(i)
      case i: DropdownBlanksInteraction => HtmlDropdownBlanksRenderer.render(i)
      case i: TableFillInInteraction => HtmlTableFillInRenderer.render(i)
      case s: Slideshow => HtmlSlideshowEditor.render(s)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.render(r)
      // plugins -- turtle
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.render(t)
      case t: TurtleStitchRecreateShapeInteraction => HtmlTurtleStitchRecreateShapeRenderer.render(t)
      // plugins -- gpt
      case g: GptInteractionElement => HtmlGptTextfieldInteractionRenderer.render(g)
      // plugins -- slideshow & reorder
      case s: Slideshow => HtmlSlideshowRenderer.render(s)
      case r: ReorderInteraction[?] => HtmlReorderInteractionRenderer.render(r)
      case r: HtmlReorderInteraction[?] @unchecked => fromElement(r, r.getDomElement())
      case e: HtmlEmbeddedDomInteraction => fromElement(e, e.domElement)

      // error
      case _: T => HtmlWorkbookElement[T](HtmlFullWorkbookApp.fullInfo, anyElement, createPlaceholderElement[T](anyElement))
    }
  }

}




