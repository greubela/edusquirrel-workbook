package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.{HtmlExerciseContainerRenderer, HtmlLangMapContentRenderer, HtmlWorkbookRenderer}
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch.HtmlTurtleStitchExploreProjectRenderer
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{ExerciseContainer, LangMapContentBasedElement, Workbook}
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchExploreProjectElement

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

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[?] = {
    anyElement match {
      case w: Workbook => HtmlWorkbookRenderer.render(w)
      case c: ExerciseContainer => HtmlExerciseContainerRenderer.render(c)
      case c: LangMapContentBasedElement => HtmlLangMapContentRenderer.render(c)
      case t: TurtleStitchExploreProjectElement => HtmlTurtleStitchExploreProjectRenderer.render(t)
      case _: T => HtmlWorkbookElement[T](HtmlFullWorkbookApp.fullInfo, anyElement, createPlaceholderElement[T](anyElement))
    }
  }

}




