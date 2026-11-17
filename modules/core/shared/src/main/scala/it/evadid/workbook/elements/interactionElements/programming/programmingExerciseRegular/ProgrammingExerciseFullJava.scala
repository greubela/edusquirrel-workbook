package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class ProgrammingExerciseFullJava(
                                        override val elementId: String,
                                        testSuite: Option[BeTestSuite] = None,
                                        startingProgram: String = ProgrammingExerciseFullJava.defaultStartingProgram,
                                        hintTask: Option[LanguageMapContentId] = None
                                      ) extends WorkbookInteractionElement[ProgrammingExerciseWithLlmScaffoldingAndGrading.type] derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExerciseFullJava.factory

  override val defaultValue: ProgrammingState = ProgrammingStateJavaString(startingProgram)
  override val gradingElements = testSuite.toList.flatMap(_.graders)
  override val scaffoldingElement = hintTask.map(task => new ProgrammingHintScaffolder(this, task))

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()
}

object ProgrammingExerciseFullJava {
  val defaultStartingProgram: String =
    """public class Main {
      |  public static void main(String[] args) {
      |  }
      |}
      |""".stripMargin

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
          element.getOptionalElementAs[String]("startingProgram", defaultStartingProgram),
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
