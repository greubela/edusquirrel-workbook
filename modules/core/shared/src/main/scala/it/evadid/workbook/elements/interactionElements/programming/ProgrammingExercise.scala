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
                                referencePython: Option[String] = None,
                                /** Editors the student may use. The first entry is the one a new exercise opens in. */
                                allowedEditors: List[ProgrammingEditorKind] = List(ProgrammingEditorKind.Snap)
) extends WorkbookInteractionElement[ProgrammingExerciseState] {
  override val associatedFactory = ProgrammingExercise.factory

  /** Palette actually shown. Dual-editor exercises cannot offer blocks Python cannot represent. */
  def effectiveEditorPalette: ProgrammingEditorPalette =
    if allowedEditors.contains(ProgrammingEditorKind.Python) && !editorPalette.pythonCompatible then
      ProgrammingEditorPalette.PythonCompatibleSnap
    else editorPalette

  override val defaultValue: ProgrammingExerciseState =
    if allowedEditors.headOption.contains(ProgrammingEditorKind.Python) then ProgrammingExerciseState.miniPython
    else ProgrammingExerciseState.mini

  override val serializerInteractionContent: Serializer[ProgrammingExerciseState] = ProgrammingExercise.StateSerializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

object ProgrammingExercise {

  val factory: WorkbookElementFactory[ProgrammingExercise] = new WorkbookElementFactory[ProgrammingExercise]() {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ProgrammingExercise = {
      val editors = element.getOptionalElementAs[List[ProgrammingEditorKind]](
        "allowedEditors",
        List(ProgrammingEditorKind.Snap)
      )
      ProgrammingExercise(
        element.elementId,
        element.getOptionalElementAs[Option[BeTestSuite]]("testSuite", None),
        element.getOptionalElementAs[ProgrammingEditorPalette]("editorPalette", ProgrammingEditorPalette.Default),
        element.getOptionalElementAs[Option[String]]("referencePython", None),
        if editors.isEmpty then List(ProgrammingEditorKind.Snap) else editors
      )
    }

    override def toSerializableElement(element: ProgrammingExercise): WorkbookElementSerializable =
      toFactoryBase(element)
        .withElementAddedAs("testSuite", element.testSuite)
        .withElementAddedAs("editorPalette", element.editorPalette)
        .withElementAddedAs("referencePython", element.referencePython)
        .withElementAddedAs("allowedEditors", element.allowedEditors)

    lazy override val writerJsonRegularRefBased: Writer[ProgrammingExercise] = macroRW
  }
  val XmlHeader = "SNAP_XML_V1"
  val PythonHeader = "PYTHON_V1"

  private final case class PythonStored(source: String, snapBase: Option[String] = None) derives ReadWriter

  /**
   * Persist format is versioned per editor.
   *
   * `SNAP_XML_V1` is a header plus the raw project XML. `PYTHON_V1` is a header
   * plus JSON `{source, snapBase}`. Legacy header-less XML stays Snap; legacy
   * header-less Python is migrated to Snap XML once, as before.
   */
  object StateSerializer extends Serializer[ProgrammingExerciseState] {
    override def serialize(obj: ProgrammingExerciseState): String =
      obj match
        case ProgrammingExerciseState.SnapXml(xml) =>
          s"$XmlHeader\n$xml"
        case ProgrammingExerciseState.PythonSource(source, snapBase) =>
          s"$PythonHeader\n${write(PythonStored(source, snapBase))}"

    override def deserialize(str: String): ProgrammingExerciseState = {
      if Option(str).forall(_.trim.isEmpty) then ProgrammingExerciseState.mini
      else parseStored(str).getOrElse(ProgrammingExerciseState.mini)
    }

    private def parseStored(str: String): Option[ProgrammingExerciseState] = {
      val trimmed = str.trim
      if trimmed.startsWith(PythonHeader) then
        parsePython(trimmed.drop(PythonHeader.length).stripLeading)
      else if trimmed.startsWith(XmlHeader) then
        val xml = trimmed.drop(XmlHeader.length).stripLeading
        if xml.isEmpty then Some(ProgrammingExerciseState.mini)
        else Some(ProgrammingExerciseState(xml))
      else if looksLikeProjectXml(trimmed) then
        Some(ProgrammingExerciseState(trimmed))
      else
        Some(migratePython(trimmed))
    }

    private def parsePython(json: String): Option[ProgrammingExerciseState] =
      Try(read[PythonStored](json)).toOption.map { stored =>
        ProgrammingExerciseState.PythonSource(stored.source, stored.snapBase)
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
