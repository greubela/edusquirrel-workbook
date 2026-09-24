package it.evadid.workbook.jsonFactory

import it.evadid.workbook.abstractions.WorkbookElement


trait WorkbookElementFactory[T <: WorkbookElement] {
  def requireIds(element: WorkbookElementSerializable): List[String]

  def createFromSerialized(factory: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T
}