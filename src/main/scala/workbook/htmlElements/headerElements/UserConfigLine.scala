package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L.*
import workbook.htmlElements.basic.HtmlButtonElement
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo
import workbook.model.info.analyze.WorkbookUserDataAnalyzer

case class UserConfigLine(fullInfo: FullInfo) extends HtmlWorkbookElement {

  private val resetButton = HtmlButtonElement.withTextLabel(fullInfo, "basic/resetLocalStorage", event => fullInfo.control.saveAndResetAllInfo())
  private val downloadDataButton = HtmlButtonElement.withTextLabel(fullInfo, "basic/downloadEverything", event => fullInfo.control.downloadAllAvailableData())

  private val domElement: Element = div(
    //cls := "hidden",
    resetButton.getDomElement(),
    downloadDataButton.getDomElement()
  )

  override def getDomElement(): Element = domElement
}
