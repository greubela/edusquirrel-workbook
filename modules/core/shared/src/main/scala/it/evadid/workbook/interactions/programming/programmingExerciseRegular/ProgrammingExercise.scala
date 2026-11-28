package it.evadid.workbook.interactions.programming.programmingExerciseRegular
import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
import it.evadid.workbook.interactions.programming.{ProgrammingExerciseEnvironment, ProgrammingTestGrader}
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, *}
import it.evadid.workbook.interactions.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.interactions.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.history.InteractionVariableHistory
import upickle.default.*

import it.evadid.workbook.interactions.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
import scala.util.Try

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class ProgrammingExercise(
                                override val elementId: String,
                                testSuite: Option[BeTestSuite] = None,
                                editorPalette: ProgrammingEditorPalette = ProgrammingEditorPalette.Default,

                                /** Optional Python turtle program whose drawing is the target for geometric matching. */
                                referencePython: Option[String] = None,
                                environment: ProgrammingEnvironment = ProgrammingEnvironment.all
                              ) extends WorkbookInteractionElement[ProgrammingExerciseFormat.type] with ProgrammingExerciseEnvironment
    derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExercise.factory

  override val defaultValue: ProgrammingState = ProgrammingStateSnapScalaModel.mini
  override val gradingElements = ProgrammingTestGrader.inEnvironment(testSuite.toList.flatMap(_.graders), environment)

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

}

object ProgrammingExercise {

  val factory: WorkbookElementFactory[ProgrammingExercise] = new WorkbookElementFactory[ProgrammingExercise]() {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ProgrammingExercise = {
      ProgrammingExercise(
        element.elementId,
        element.getOptionalElementAs[Option[BeTestSuite]]("testSuite", None),
        element.getOptionalElementAs[ProgrammingEditorPalette]("editorPalette", ProgrammingEditorPalette.Default),
        element.getOptionalElementAs[Option[String]]("referencePython", None),
        element.getOptionalElementAs[ProgrammingEnvironment]("environment", ProgrammingEnvironment.all)
      )
    }

    override def toSerializableElement(element: ProgrammingExercise): WorkbookElementSerializable =
      val serialized = toFactoryBase(element)
        .withElementAddedAs("testSuite", element.testSuite)
        .withElementAddedAs("editorPalette", element.editorPalette)
        .withElementAddedAs("referencePython", element.referencePython)
      if element.environment == ProgrammingEnvironment.all then serialized else serialized.withElementAddedAs("environment", element.environment)

    lazy override val writerJsonRegularRefBased: Writer[ProgrammingExercise] = macroRW
  }

}
