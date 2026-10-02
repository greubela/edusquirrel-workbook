package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.*

import scala.util.Try

case class ProgrammingExercise(
                                override val elementId: String,
                                testSuite: Option[BeTestSuite] = None,
                                editorPalette: ProgrammingEditorPalette = ProgrammingEditorPalette.Default,
                                /** Optional Python turtle program whose drawing is the target for geometric matching. */
                                referencePython: Option[String] = None
) extends WorkbookInteractionElement[ProgrammingExerciseState] {
  override val associatedFactory = ProgrammingExercise.factory

  override val defaultValue: ProgrammingExerciseState = ProgrammingExerciseState.mini

  override val serializerInteractionContent: Serializer[ProgrammingExerciseState] = ProgrammingExercise.StateSerializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

object ProgrammingExercise {

  val factory: WorkbookElementFactory[ProgrammingExercise] = new WorkbookElementFactory[ProgrammingExercise]() {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ProgrammingExercise = {
      ProgrammingExercise(
        element.elementId,
        element.getOptionalElementAs[Option[BeTestSuite]]("testSuite", None),
        element.getOptionalElementAs[ProgrammingEditorPalette]("editorPalette", ProgrammingEditorPalette.Default),
        element.getOptionalElementAs[Option[String]]("referencePython", None)
      )
    }

    override def toSerializableElement(element: ProgrammingExercise): WorkbookElementSerializable =
      toFactoryBase(element)
        .withElementAddedAs("testSuite", element.testSuite)
        .withElementAddedAs("editorPalette", element.editorPalette)
        .withElementAddedAs("referencePython", element.referencePython)

    lazy override val writerJsonRegularRefBased: Writer[ProgrammingExercise] = macroRW
  }
  val XmlHeader = "SNAP_XML_V1"

  /** Canonical persist format: versioned Snap project XML. Legacy Python migrates on read. */
  object StateSerializer extends Serializer[ProgrammingExerciseState] {
    override def serialize(obj: ProgrammingExerciseState): String =
      s"$XmlHeader\n${obj.snapXml}"

    override def deserialize(str: String): ProgrammingExerciseState = {
      if Option(str).forall(_.trim.isEmpty) then ProgrammingExerciseState.mini
      else parseStored(str).getOrElse(ProgrammingExerciseState.mini)
    }

    private def parseStored(str: String): Option[ProgrammingExerciseState] = {
      val trimmed = str.trim
      if trimmed.startsWith(XmlHeader) then
        val xml = trimmed.drop(XmlHeader.length).stripLeading
        if xml.isEmpty then Some(ProgrammingExerciseState.mini)
        else Some(ProgrammingExerciseState(xml))
      else if looksLikeProjectXml(trimmed) then
        Some(ProgrammingExerciseState(trimmed))
      else
        Some(migratePython(trimmed))
    }

    private def migratePython(python: String): ProgrammingExerciseState = {
      val program = Try(BeProgram.fromPythonString(python)).getOrElse(BeProgram.miniProgram())
      ProgrammingExerciseState.fromProgram(program)
    }

    private def looksLikeProjectXml(trimmed: String): Boolean =
      trimmed.startsWith("<") &&
        (trimmed.contains("<project") || trimmed.startsWith("<?xml"))
  }
}
