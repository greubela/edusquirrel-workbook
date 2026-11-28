package it.evadid.workbook.elements.display

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.serialization.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.serialization.WorkbookElementFactory
import upickle.default.*

/** A form that hands in the open workbook's answers under an entered name and address, without an account. */
case class WorkbookSubmissionElement(override val elementId: String) extends WorkbookDisplayElement {
  override val associatedFactory: WorkbookElementFactory[WorkbookSubmissionElement] = WorkbookSubmissionElement.factory
}

object WorkbookSubmissionElement {

  val factory: NoContentElementFactory[WorkbookSubmissionElement] = new NoContentElementFactory[WorkbookSubmissionElement] {
    override def callConstructor(elementId: String): WorkbookSubmissionElement = WorkbookSubmissionElement(elementId)
  }
}
