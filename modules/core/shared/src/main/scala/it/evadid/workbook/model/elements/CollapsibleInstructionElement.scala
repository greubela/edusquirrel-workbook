package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement

case class CollapsibleInstructionElement(
  titleLabel: LanguageMapContentId,
  bodyContent: LanguageMapContentId,
  initiallyCollapsed: Boolean = true
) extends WorkbookElement