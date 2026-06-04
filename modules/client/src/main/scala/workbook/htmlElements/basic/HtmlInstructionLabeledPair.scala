package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

// workbook element that renders a labeled pair: a title and a body from separate language map IDs

case class HtmlInstructionLabeledPair(
  fullInfo: FullInfo,
  titleMapId: String,
  bodyMapId: String,
  cssClass: String = "instruction-pair"
) extends HtmlWorkbookElement {

  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    cls := cssClass,
    div(
      cls := s"${cssClass}__title",
      text <-- fullInfo.signals.stringFromLanguageMapId(titleMapId)
    ),
    div(
      cls := s"${cssClass}__body",
      text <-- fullInfo.signals.stringFromLanguageMapId(bodyMapId)
    )
  )
}