package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.storage.AsyncDataCache
import workbook.model.abstractions.*
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

import scala.concurrent.ExecutionContext

case class HtmlContainerTitle(workbookInfo: AllWorkbookInfo, titleSignal: Signal[String]) extends HtmlWorkbookElement {

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

  def apply(workbookInfo: AllWorkbookInfo, languageMapId: String): HtmlContainerTitle =
    HtmlContainerTitle(workbookInfo, workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global))


}