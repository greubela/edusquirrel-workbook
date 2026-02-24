package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.*
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.HtmlWorkbookTitleLine

case class Workbook(workbookInfoVar: Var[WorkbookInfo], title: LanguageMap[HumanLanguage], sections: List[WorkbookSection]) extends HtmlWorkbookElement {


  private val titleLine = HtmlWorkbookTitleLine(workbookInfoVar, title)

  override def getDomElement(): L.Element = L.div(
    L.cls := "workbook",
    titleLine.getDomElement(),
    sections.head.getDomElement()
  )


}
