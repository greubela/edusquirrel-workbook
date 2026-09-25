package it.evadid.workbook.jsonFactory

import it.evadid.workbook.abstractions.WorkbookElement
import upickle.ReadWriter
import upickle.default.*

case class WorkbookElementReference(referencedId: String, referencedType: String) derives ReadWriter {
  def resolveWith(elements: List[WorkbookElement]): Option[WorkbookElement] = elements.find(_.elementId == referencedId)
}
