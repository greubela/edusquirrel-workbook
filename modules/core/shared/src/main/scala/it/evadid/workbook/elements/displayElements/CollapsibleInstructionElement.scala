package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookDisplayElement

case class CollapsibleInstructionElement(
  titleLabel: LanguageMapContentId,
  bodyContent: LanguageMapContentId,
  initiallyCollapsed: Boolean = true
) extends WorkbookDisplayElement
