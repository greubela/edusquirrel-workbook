package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.*
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class Workbook(workbookInfoVar: Var[WorkbookInfo], title: LanguageMap[HumanLanguage], sections: List[WorkbookSection]) extends HtmlWorkbookElement{

  override def getDomElement(): L.Element = sections.head.getDomElement()


}
