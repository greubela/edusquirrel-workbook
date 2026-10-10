package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.Serializer
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class ProgrammingExerciseFullJava(
    override val elementId: String,
    testSuite: Option[BeTestSuite] = None
) extends WorkbookInteractionElement[ProgrammingState] derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExerciseFullJava.factory

  override val defaultValue: ProgrammingState =
    ProgrammingStateJavaString(
      """public class Main {
        |  public static void main(String[] args) {
        |  }
        |}
        |""".stripMargin
    )

  override val serializerInteractionContent: Serializer[ProgrammingState] = ProgrammingExercise.StateSerializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

object ProgrammingExerciseFullJava {
  val factory: WorkbookElementFactory[ProgrammingExerciseFullJava] =
    new WorkbookElementFactory[ProgrammingExerciseFullJava]() {
      override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

      override def fromSerializedElement(
          element: WorkbookElementSerializable,
          parsedElements: Map[String, WorkbookElement]
      ): ProgrammingExerciseFullJava =
        ProgrammingExerciseFullJava(
          element.elementId,
          element.getOptionalElementAs[Option[BeTestSuite]]("testSuite", None)
        )

      override def toSerializableElement(element: ProgrammingExerciseFullJava): WorkbookElementSerializable =
        toFactoryBase(element).withElementAddedAs("testSuite", element.testSuite)

      lazy override val writerJsonRegularRefBased: Writer[ProgrammingExerciseFullJava] = macroRW
    }
}
