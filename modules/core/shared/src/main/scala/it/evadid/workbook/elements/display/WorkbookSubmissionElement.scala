package it.evadid.workbook.elements.display

import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.serialization.WorkbookElementFactory.NoRefsElementFactory
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** A form that hands in the open workbook's answers under an entered name and address, without an account. */
case class WorkbookSubmissionElement(override val elementId: String) extends WorkbookDisplayElement derives ReadWriter {
  override val associatedFactory: WorkbookElementFactory[WorkbookSubmissionElement] = WorkbookSubmissionElement.factory
}

object WorkbookSubmissionElement {

  val factory: NoRefsElementFactory[WorkbookSubmissionElement] = new NoRefsElementFactory[WorkbookSubmissionElement]() {

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, Seq[VariableDisplayConfig]] =
      Map(0 -> List(VariableDisplayConfig("elementId", true)))

    override lazy val writerJsonRegularRefBased: Writer[WorkbookSubmissionElement] = WorkbookSubmissionElement.derived$ReadWriter

    override def fromSerializedElement(f: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): WorkbookSubmissionElement =
      WorkbookSubmissionElement(f.elementId)
  }
}
