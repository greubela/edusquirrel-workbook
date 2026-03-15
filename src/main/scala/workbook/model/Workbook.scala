package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.*
import contentmanagement.storage.DataStorage
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.head.HtmlWorkbookHeader

case class Workbook(workbookInfoVar: Var[WorkbookInfo], title: LanguageMap[HumanLanguage], sections: List[WorkbookSection]) extends HtmlWorkbookElement {


  private def getElement(activeSection: Option[WorkbookSection]): Element =
    if (activeSection.isEmpty) {
      span(
        text <-- DataStorage.labelSignalFromLanguageMapName("noSectionSelected", workbookInfoVar)
      )
    } else {
      activeSection.get.getDomElement()
    }

  private val titleLine = HtmlWorkbookHeader(workbookInfoVar, title, sections)

  override def getDomElement(): L.Element = L.div(
    cls := "workbook",
    div(
      cls := "workbook-header",
      titleLine.getDomElement(),
    ),
    div(
      cls := "workbook-body",
      child <-- workbookInfoVar.signal.mapLazy(curInfo => getElement(curInfo.config.activeSection))
    )

  )


}
