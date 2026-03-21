package workbook.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.*
import workbook.model.interaction.history.*

trait HtmlWorkbookElement extends HtmlAppElement {
  def workbookInfoVar: Var[WorkbookInfo]
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