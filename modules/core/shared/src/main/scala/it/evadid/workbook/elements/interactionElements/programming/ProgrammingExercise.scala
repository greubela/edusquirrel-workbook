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
) extends WorkbookInteractionElement[ProgrammingState] {
  override val associatedFactory = ProgrammingExercise.factory

  override val defaultValue: ProgrammingState = ProgrammingState.mini

  override val serializerInteractionContent: Serializer[ProgrammingState] = ProgrammingExercise.StateSerializer

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
  val StateHeader = "PROGRAMMING_STATE_V1"

  /** Tagged persist format retaining the active representation. Legacy Snap/Python values migrate on read. */
  object StateSerializer extends Serializer[ProgrammingState] {
    override def serialize(obj: ProgrammingState): String = obj match
      case ProgrammingStateSnapXml(xml) => s"$StateHeader\nSNAP_XML\n$xml"
      case ProgrammingStatePythonString(python) => s"$StateHeader\nPYTHON\n$python"
      case ProgrammingStateJavaString(java) => s"$StateHeader\nJAVA\n$java"
      case ProgrammingStateBeExpression(expression) =>
        s"$StateHeader\nBE_EXPRESSION\n${SnapTurtlePythonBridge.printedPython(expression)}"

    override def deserialize(str: String): ProgrammingState = {
      if Option(str).forall(_.trim.isEmpty) then ProgrammingState.mini
      else parseStored(str).getOrElse(ProgrammingState.mini)
    }

    private def parseStored(str: String): Option[ProgrammingState] = {
      val trimmed = str.trim
      if trimmed.startsWith(StateHeader) then
        val payload = trimmed.drop(StateHeader.length).stripLeading
        val newline = payload.indexOf('\n')
        val (kind, value) = if newline < 0 then (payload, "") else (payload.take(newline), payload.drop(newline + 1))
        kind match
          case "SNAP_XML" => Some(ProgrammingStateSnapXml(value))
          case "PYTHON" => Some(ProgrammingStatePythonString(value))
          case "JAVA" => Some(ProgrammingStateJavaString(value))
          case "BE_EXPRESSION" => Try(ProgrammingStateBeExpression(BeProgram.fromPythonString(value).fullProgram)).toOption
          case _ => None
      else if trimmed.startsWith(XmlHeader) then
        val xml = trimmed.drop(XmlHeader.length).stripLeading
        if xml.isEmpty then Some(ProgrammingState.mini)
        else Some(ProgrammingStateSnapXml(xml))
      else if looksLikeProjectXml(trimmed) then
        Some(ProgrammingStateSnapXml(trimmed))
      else
        Some(migratePython(trimmed))
    }

    private def migratePython(python: String): ProgrammingState = {
      val program = Try(BeProgram.fromPythonString(python)).getOrElse(BeProgram.miniProgram())
      ProgrammingExerciseState.fromProgram(program)
    }

    private def looksLikeProjectXml(trimmed: String): Boolean =
      trimmed.startsWith("<") &&
        (trimmed.contains("<project") || trimmed.startsWith("<?xml"))
  }
}
