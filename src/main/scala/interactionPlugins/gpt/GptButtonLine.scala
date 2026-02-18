package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.{HtmlWorkbookElement, WorkbookInteraction}

case class GptButtonLine(workbookInfoVar: Var[WorkbookInfo], textInteraction: WorkbookInteraction[String]) extends HtmlWorkbookElement {

  
  private val htmlGPTMessenger = HtmlGPTMessenger(workbookInfoVar, textInteraction)
  private var htmlGptGrader = HtmlGptGrader(workbookInfoVar, textInteraction)

  override def getDomElement(): L.Element =    div(
    cls := "button-line",
    htmlGPTMessenger.getDomElement(),
    htmlGptGrader.getDomElement()
  )
}
