package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class CollapsibleInstructionElement(
  titleLabel: LanguageMapContentId,
  bodyContent: LanguageMapContentId,
  initiallyCollapsed: Boolean = true
) extends WorkbookDisplayElement {
  override val elementId: String = ???

  override def toSerializableType: WorkbookElementFactory = ???
}
