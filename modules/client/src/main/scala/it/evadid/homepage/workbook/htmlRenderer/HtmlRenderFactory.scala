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
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch.{HtmlTurtleStitchExploreProjectRenderer, HtmlTurtleStitchRecreateShapeRenderer}
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{ExerciseContainer, ImageElement, LangMapContentBasedElement, Workbook}
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import it.evadid.workbook.model.interaction.basic.LabeledCheckboxInteraction
import it.evadid.workbook.plugins.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}

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
      case i: ImageElement => fromElement[ImageElement](i, HtmlImageElement(i.location).getDomElement())
      // interactions
      case i: TextInteractionBasic => HtmlSimpleTextInteractionRenderer.render(i)
      case i: LabeledCheckboxInteraction => HtmlBasicCheckboxRenderer.render(i)
      // plugins
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.render(t)
      case t: TurtleStitchRecreateShapeInteraction => HtmlTurtleStitchRecreateShapeRenderer.render(t)
      // error
      case _: T => HtmlWorkbookElement[T](HtmlFullWorkbookApp.fullInfo, anyElement, createPlaceholderElement[T](anyElement))
    }
  }

}




