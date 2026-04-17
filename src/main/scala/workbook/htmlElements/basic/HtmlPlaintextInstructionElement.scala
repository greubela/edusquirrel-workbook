package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

import scala.concurrent.ExecutionContext

case class HtmlPlaintextInstructionElement(workbookInfo: AllWorkbookInfo, contentSignal: Signal[String]) extends HtmlWorkbookElement {

  override def getDomElement(): L.Element = div(
    cls := "workbook-element exercise-instruction",
    div(cls := "instruction-content",
      text <-- contentSignal
    )
  )
}

object HtmlPlaintextInstructionElement {

  def apply(workbookInfo: AllWorkbookInfo, languageMapId: String): HtmlPlaintextInstructionElement = {
    val signal: Signal[String] = workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global)
    HtmlPlaintextInstructionElement(workbookInfo, signal)
  }
  
}