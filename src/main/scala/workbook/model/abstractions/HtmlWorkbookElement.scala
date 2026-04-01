package workbook.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.model.interaction.*
import workbook.model.interaction.history.*

trait HtmlWorkbookElement extends HtmlAppElement {
    def workbookInfo: AllWorkbookInfo
    def workbookInfoVar: Var[WorkbookInfo] = workbookInfo.workbookInfoVar
}


trait WorkbookInteraction[T] extends HtmlWorkbookElement {
  def id: String

  def interactionVariable: InteractionVariable[T]
}

trait WorkbookScaffolding[T] {
  def underlyingInteraction: WorkbookInteraction[T]
}

object HtmlWorkbookElement {



}