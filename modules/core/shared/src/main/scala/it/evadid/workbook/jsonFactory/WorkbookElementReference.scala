package it.evadid.workbook.jsonFactory

import it.evadid.workbook.abstractions.WorkbookElement

case class WorkbookElementReference(referencedId: String, referencedType: String) {
  def resolveWith(elements: List[WorkbookElement]): Option[WorkbookElement] = elements.find(_.elementId == referencedId)
}
