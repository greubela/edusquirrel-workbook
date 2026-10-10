package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXMLWithAdditionalFloatingObjects, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingEditorPalette, ProgrammingStateSnapXmlHelper, SnapTurtlePythonBridge}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
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

  override val defaultValue: ProgrammingState = ProgrammingStateSnapXmlHelper.mini

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
  val StateHeader = "PROGRAMMING_STATE_V2"

  /** Versioned tagged format. The previous Snap XML and Python formats remain readable. */
  object StateSerializer extends Serializer[ProgrammingState] {
    override def serialize(obj: ProgrammingState): String = obj match
      case ProgrammingStateSnapXml(xml) => s"$StateHeader\nSNAP_XML\n$xml"
      case ProgrammingStateSnapXMLWithAdditionalFloatingObjects(xml, objects) =>
        s"$StateHeader\nSNAP_XML_WITH_FLOATING\n${write(objects)}\n$xml"
      case ProgrammingStatePythonString(code) => s"$StateHeader\nPYTHON\n$code"
      case ProgrammingStateJavaString(code) => s"$StateHeader\nJAVA\n$code"
      case ProgrammingStateBeExpression(expression) =>
        s"$StateHeader\nBE_EXPRESSION\n${SnapTurtlePythonBridge.printedPython(expression)}"

    override def deserialize(str: String): ProgrammingState = {
      if Option(str).forall(_.trim.isEmpty) then ProgrammingStateSnapXmlHelper.mini
      else parseStored(str).getOrElse(ProgrammingStateSnapXmlHelper.mini)
    }

    private def parseStored(str: String): Option[ProgrammingState] = {
      val trimmed = str.trim
      if trimmed.startsWith(StateHeader) then parseVersion2(str.stripLeading)
      else if trimmed.startsWith(XmlHeader) then
        val xml = trimmed.drop(XmlHeader.length).stripLeading
        if xml.isEmpty then Some(ProgrammingStateSnapXmlHelper.mini)
        else Some(ProgrammingStateSnapXml(xml))
      else if looksLikeProjectXml(trimmed) then
        Some(ProgrammingStateSnapXml(trimmed))
      else
        Some(migratePython(trimmed))
    }

    private def parseVersion2(stored: String): Option[ProgrammingState] = {
      val body = stored.drop(StateHeader.length).stripLeading
      val newline = body.indexOf('\n')
      val (kind, payload) = if newline < 0 then (body, "") else (body.take(newline), body.drop(newline + 1))
      kind.stripSuffix("\r") match
        case "SNAP_XML" => Some(ProgrammingStateSnapXml(payload))
        case "PYTHON" => Some(ProgrammingStatePythonString(payload))
        case "JAVA" => Some(ProgrammingStateJavaString(payload))
        case "BE_EXPRESSION" =>
          Try(BeProgram.fromPythonString(payload).fullProgram).toOption.map(ProgrammingStateBeExpression(_))
        case "SNAP_XML_WITH_FLOATING" =>
          val split = payload.indexOf('\n')
          if split < 0 then None
          else Try(read[List[String]](payload.take(split))).toOption.map(objects =>
            ProgrammingStateSnapXMLWithAdditionalFloatingObjects(payload.drop(split + 1), objects)
          )
        case _ => None
    }

    private def migratePython(python: String): ProgrammingState = {
      val program = Try(BeProgram.fromPythonString(python)).getOrElse(BeProgram.miniProgram())
      ProgrammingStateSnapXmlHelper.fromProgram(program)
    }

    private def looksLikeProjectXml(trimmed: String): Boolean =
      trimmed.startsWith("<") &&
        (trimmed.contains("<project") || trimmed.startsWith("<?xml"))
  }
}
