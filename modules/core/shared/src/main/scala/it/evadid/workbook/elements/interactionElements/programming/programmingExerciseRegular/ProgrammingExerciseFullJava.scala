package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExerciseEnvironment, ProgrammingTestGrader}
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** Data a program changes through the provided libraries; the editor shows it after running `main`. */
enum ExerciseDataView derives ReadWriter {
  case NumberList, Sudoku
}

/**
 * A Java class program. Without `dataView` the editor previews turtle drawings; with it, the editor
 * shows the number list or sudoku after `main` instead. `instructionText` appears beside the editor.
 */
case class ProgrammingExerciseFullJava(
                                        override val elementId: String,
                                        testSuite: Option[BeTestSuite] = None,
                                        startingProgram: String = ProgrammingExerciseFullJava.defaultStartingProgram,
                                        hintTask: Option[LanguageMapContentId] = None,
                                        instructionText: Option[LanguageMapContentId] = None,
                                        dataView: Option[ExerciseDataView] = None,
                                        environment: ProgrammingEnvironment = ProgrammingEnvironment.all
                                      ) extends WorkbookInteractionElement[ProgrammingExerciseWithLlmScaffoldingAndGrading.type]
    with ProgrammingExerciseEnvironment derives upickle.default.ReadWriter {
  override val associatedFactory = ProgrammingExerciseFullJava.factory

  override val defaultValue: ProgrammingState = ProgrammingStateRaw(Java, startingProgram)
  override val gradingElements = ProgrammingTestGrader.inEnvironment(testSuite.toList.flatMap(_.graders), environment)
  override val scaffoldingElement = hintTask.map(task =>
    new ProgrammingHintScaffolder(ProgrammingHintScaffolder.Exercise(defaultValue, gradingElements), task))

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
          element.getOptionalElementAs[Option[LanguageMapContentId]]("hintTask", None),
          element.getOptionalElementAs[Option[LanguageMapContentId]]("instructionText", None),
          element.getOptionalElementAs[Option[ExerciseDataView]]("dataView", None),
          element.getOptionalElementAs[ProgrammingEnvironment]("environment", ProgrammingEnvironment.all)
        )

      override def toSerializableElement(element: ProgrammingExerciseFullJava): WorkbookElementSerializable =
        val base = toFactoryBase(element).withElementAddedAs("testSuite", element.testSuite)
        val withSource = if element.startingProgram == defaultStartingProgram then base
        else base.withElementAdded("startingProgram", element.startingProgram)
        val withHint = element.hintTask.fold(withSource)(_ => withSource.withElementAddedAs("hintTask", element.hintTask))
        val withInstruction = element.instructionText.fold(withHint)(_ => withHint.withElementAddedAs("instructionText", element.instructionText))
        val withView = element.dataView.fold(withInstruction)(_ => withInstruction.withElementAddedAs("dataView", element.dataView))
        if element.environment == ProgrammingEnvironment.all then withView else withView.withElementAddedAs("environment", element.environment)

      lazy override val writerJsonRegularRefBased: Writer[ProgrammingExerciseFullJava] = macroRW
    }
}
