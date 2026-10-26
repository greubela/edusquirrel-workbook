package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.Serializer
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.abstractions.scaffolding.ProgrammingHintScaffolder
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class ProgrammingExerciseFullJava(
    override val elementId: String,
    testSuite: Option[BeTestSuite] = None,
    startingProgram: String = ProgrammingExerciseFullJava.defaultStartingProgram,
    hintTask: Option[LanguageMapContentId] = None
) extends WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any] derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExerciseFullJava.factory

  override val defaultValue: ProgrammingState = ProgrammingStateJavaString(startingProgram)
  override val gradingElements = testSuite.toList.map(ProgrammingUnitTestGrader(_))
  override val scaffoldingElement = hintTask.map(task => new ProgrammingHintScaffolder[Any](this, task))

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

object ProgrammingExerciseFullJava {
  val defaultStartingProgram: String = """public class Main {
    |  public static void main(String[] args) {
    |  }
    |}
    |""".stripMargin

  private def startingProgramFromSerialized(element: WorkbookElementSerializable): String =
    element.allConstructorFields.get("startingProgram").map(_.str).getOrElse {
      element.getOptionalElementAs[Option[ujson.Value]]("turtleTask", None)
        .map(_("startingProgram").str).getOrElse(defaultStartingProgram)
    }

  val factory: WorkbookElementFactory[ProgrammingExerciseFullJava] =
    new WorkbookElementFactory[ProgrammingExerciseFullJava]() {
      override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

      override def fromSerializedElement(
          element: WorkbookElementSerializable,
          parsedElements: Map[String, WorkbookElement]
      ): ProgrammingExerciseFullJava =
        ProgrammingExerciseFullJava(
          element.elementId,
          element.getOptionalElementAs[Option[BeTestSuite]]("testSuite", None),
          startingProgramFromSerialized(element),
          element.getOptionalElementAs[Option[LanguageMapContentId]]("hintTask", None)
        )

      override def toSerializableElement(element: ProgrammingExerciseFullJava): WorkbookElementSerializable =
        val base = toFactoryBase(element).withElementAddedAs("testSuite", element.testSuite)
        val withSource = if element.startingProgram == defaultStartingProgram then base
        else base.withElementAdded("startingProgram", element.startingProgram)
        element.hintTask.fold(withSource)(_ => withSource.withElementAddedAs("hintTask", element.hintTask))

      lazy override val writerJsonRegularRefBased: Writer[ProgrammingExerciseFullJava] = macroRW
    }
}
