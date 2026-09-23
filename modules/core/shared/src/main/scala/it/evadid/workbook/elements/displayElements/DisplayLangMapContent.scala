package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{LangMapContentIdType, WorkbookDisplayElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class DisplayLangMapContent(content: LanguageMapContentId, contentType: LangMapContentIdType) extends WorkbookDisplayElement {

  override val elementId: String = ???

  override def toSerializableType: WorkbookElementFactory = ???
}



