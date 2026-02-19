package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.WorkbookExerciseTitle

case class HtmlContainerTitle(workbookInfoVar: Var[WorkbookInfo], titleMap: LanguageMap[HumanLanguage]) extends WorkbookExerciseTitle {

  override def getDomElement(): Element = {
    div(
      cls := "container-title",
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(titleMap.getInLanguage)
    )
  }


}
