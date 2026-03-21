package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.abstractions.WorkbookExerciseTitle
import workbook.model.info.WorkbookInfo

case class HtmlContainerTitle(workbookInfoVar: Var[WorkbookInfo], titleMap: LanguageMap[HumanLanguage]) extends WorkbookExerciseTitle {

  override def getDomElement(): Element = {
    div(
      cls := "workbook-element container-title",
      h2(
        child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(titleMap.getInLanguage)
      )
    )
  }


}
