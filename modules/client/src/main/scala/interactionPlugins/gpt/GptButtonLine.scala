package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.{FullInfo, HomepageInfo}

case class GptButtonLine(fullInfo: FullInfo, textInteraction: WorkbookInteraction[String]) extends HtmlWorkbookElement {

  
  private val htmlGPTMessenger = HtmlGPTMessenger(fullInfo, textInteraction)
  private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)

  override def getDomElement(): L.Element =    div(
    cls := "button-line",
    htmlGPTMessenger.getDomElement(),
    htmlGptGrader.getDomElement()
  )
}
