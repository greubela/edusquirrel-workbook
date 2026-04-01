package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

case class GptButtonLine(workbookInfo: AllWorkbookInfo, textInteraction: WorkbookInteraction[String]) extends HtmlWorkbookElement {

  
  private val htmlGPTMessenger = HtmlGPTMessenger(workbookInfo, textInteraction)
  private var htmlGptGrader = HtmlGptGrader(workbookInfo, textInteraction)

  override def getDomElement(): L.Element =    div(
    cls := "button-line",
    htmlGPTMessenger.getDomElement(),
    htmlGptGrader.getDomElement()
  )
}
