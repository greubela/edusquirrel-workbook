package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.storage.DataStorage
import workbook.model.abstractions.*
import workbook.model.info.WorkbookInfo

case class HtmlContainerTitle(workbookInfoVar: Var[WorkbookInfo], titleSignal: Signal[String]) extends HtmlWorkbookElement {

  override def getDomElement(): Element = {
    div(
      cls := "workbook-element container-title",
      h2(
        text <-- titleSignal //workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(titleMap.getInLanguage)
      )
    )
  }


}

object HtmlContainerTitle {

  def apply(workbookInfoVar: Var[WorkbookInfo], languageMapId: String): HtmlContainerTitle = HtmlContainerTitle(workbookInfoVar, DataStorage.labelSignalFromLanguageMapName(languageMapId, workbookInfoVar))

  def apply(workbookInfoVar: Var[WorkbookInfo], languageMap: LanguageMap[HumanLanguage])  = {
    println("[WARN] language Map that got not transferred to a proper file: " + languageMap)
    new HtmlContainerTitle(workbookInfoVar, workbookInfoVar.signal.map(_.languageStringFromMap(languageMap)))
  }

}