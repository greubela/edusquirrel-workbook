package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingEditorPalette, ProgrammingStateSnapXmlHelper, SnapTurtlePythonBridge}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

import scala.util.Try

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class ProgrammingExercise(
                                override val elementId: String,
                                testSuite: Option[BeTestSuite] = None,
                                editorPalette: ProgrammingEditorPalette = ProgrammingEditorPalette.Default,

                                /** Optional Python turtle program whose drawing is the target for geometric matching. */
                                referencePython: Option[String] = None
                              ) extends WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any] derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExercise.factory

  override val defaultValue: ProgrammingState = ProgrammingStateSnapXmlHelper.mini
  override val gradingElements = testSuite.toList.map(ProgrammingUnitTestGrader(_))


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



}
