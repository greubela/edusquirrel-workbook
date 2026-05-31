package it.evadid.homepage.workbook.htmlRenderer

import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlWorkbookRenderer
import it.evadid.workbook.model.elements.Workbook

trait HtmlRenderFactory[T <: WorkbookElement] {

  protected def fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo

  protected def createDomElement(workbookElement: T): Element

  def render(workbookElement: T): HtmlWorkbookElement[T] = HtmlWorkbookElement[T](fullInfo, workbookElement, createDomElement(workbookElement))

  def contentIdStringSignal(contentId: LanguageMapContentId): Signal[String] = {
    fullInfo.signals.stringFromLanguageMapId(contentId)
  }
  
}

object HtmlRenderFactory {

  implicit class HtmlDefaultRenderingOfWorkbookElement[T <: WorkbookElement](workbookElement: T) {
    def defaultRendering: HtmlWorkbookElement[?] = renderWorkbookElement(workbookElement)
  }

  private def createPlaceholderElement[T <: WorkbookElement](workbookElement: T): Element = {
    div("HtmlRenderFactory::renderWorkbookElement cannot yet render objects of type '" + workbookElement.getClass.getName + "'!")
  }

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T): HtmlWorkbookElement[?] = {
    anyElement match {
      case w: Workbook => HtmlWorkbookRenderer.render(w)
      case _: T => HtmlWorkbookElement[T](HtmlFullWorkbookApp.fullInfo, anyElement, createPlaceholderElement[T](anyElement))
    }
  }

}




