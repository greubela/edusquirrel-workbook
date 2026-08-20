package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

import scala.util.Try

case class ProgrammingExercise(
    override val id: String,
    testSuite: Option[BeTestSuite] = None,
    editorPalette: ProgrammingEditorPalette = ProgrammingEditorPalette.Default
) extends WorkbookInteractionElement[ProgrammingExerciseState] {

  override val defaultValue: ProgrammingExerciseState = ProgrammingExerciseState.mini

  override val serializer: Serializer[ProgrammingExerciseState] = ProgrammingExercise.StateSerializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

object ProgrammingExercise {

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
