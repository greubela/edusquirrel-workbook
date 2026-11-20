package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{English, German, Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.simulation.data.{DataWorld, SudokuGrid}
import it.evadid.vm.simulation.turtle.TurtleExecutionLimits
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.DataExpectation.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.meta.ExecutionContextInformation
import munit.FunSuite
import upickle.default.*

import scala.concurrent.ExecutionContext

class ProgramDataTestGraderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val description = LanguageMapContentId("test/description")
  private def python(code: String): ProgrammingState = ProgrammingStateRaw(Python, code)
  private def grader(function: String, world: DataWorld, expectations: DataExpectation*) =
    ProgramDataTestGrader("data-test", description, function, Nil, world, expectations)
  private def german(result: (GradingStatus, Option[it.evadid.core.datastructures.language.LanguageMap[it.evadid.core.datastructures.language.AppLanguage.HumanLanguage]])) =
    result._2.map(_.getInLanguage(German)).getOrElse("")

  private val bubble = python(
    """def sortiere():
      |    n = NumberList.length()
      |    for d in range(n):
      |        for i in range(n - 1 - d):
      |            if NumberList.get(i) > NumberList.get(i + 1):
      |                NumberList.swap(i, i + 1)
      |""".stripMargin)
  private val selection = python(
    """def sortiere():
      |    n = NumberList.length()
      |    for i in range(n):
      |        k = i
      |        for j in range(i + 1, n):
      |            if NumberList.get(j) < NumberList.get(k):
      |                k = j
      |        NumberList.swap(i, k)
      |""".stripMargin)
  private val list = DataWorld(list = Vector(5, 1, 4, 2, 8))

  test("list expectations accept the right result and explain a wrong one") {
    assertEquals(grader("sortiere", list, ListEquals(Seq(1, 2, 4, 5, 8))).evaluate(bubble)._1, GradingStatus.Correct())
    val wrong = grader("sortiere", list, ListEquals(Seq(8, 5, 4, 2, 1))).evaluate(bubble)
    assertEquals(wrong._1, GradingStatus.Incorrect())
    assert(german(wrong).contains("[8, 5, 4, 2, 1]") && german(wrong).contains("[1, 2, 4, 5, 8]"), german(wrong))
    assert(wrong._2.get.getInLanguage(English).contains("should be"))
  }

  test("command expectations distinguish bubble sort from selection sort") {
    val bubbleRules = Seq(OnlyAdjacentSwaps, SwapCount(ProgramDataTestGrader.inversions(list.list)))
    assertEquals(grader("sortiere", list, bubbleRules *).evaluate(bubble)._1, GradingStatus.Correct())
    val bySelection = grader("sortiere", list, bubbleRules *).evaluate(selection)
    assertEquals(bySelection._1, GradingStatus.Incorrect())
    assert(german(bySelection).contains("benachbarte"), german(bySelection))
    assertEquals(grader("sortiere", list, UsesListOperation(ListOperation.Swap), AvoidsListOperation(ListOperation.Set)).evaluate(selection)._1,
      GradingStatus.Correct())
    assert(german(grader("sortiere", list, AvoidsListOperation(ListOperation.Swap)).evaluate(selection)).contains("NumberList.swap"))
    assert(german(grader("sortiere", list, UsesListOperation(ListOperation.Set)).evaluate(selection)).contains("NumberList.set"))
    assert(german(grader("sortiere", list, SwapCount(0)).evaluate(selection)).contains("genau 0"))
  }

  test("inversions count the pairs in the wrong order") {
    assertEquals(ProgramDataTestGrader.inversions(Seq(5, 1, 4, 2, 8)), 4)
    assertEquals(ProgramDataTestGrader.inversions(Seq(1, 2, 3)), 0)
    assertEquals(ProgramDataTestGrader.inversions(Seq(3, 2, 1)), 3)
    assertEquals(ProgramDataTestGrader.inversions(Seq(2, 2)), 0)
  }

  test("return and sudoku expectations check the value and the grid") {
    val fits = python("def leer():\n    return Sudoku.get(0, 2) == 0\ndef fuelle():\n    Sudoku.set(0, 2, 4)\n    Sudoku.set(4, 3, 8)\n    Sudoku.set(8, 8, 9)\n    return True\n")
    assertEquals(grader("leer", DataWorld(), Returns(true)).evaluate(fits)._1, GradingStatus.Correct())
    assert(german(grader("leer", DataWorld(), Returns(false)).evaluate(fits)).contains("false"))
    assertEquals(grader("fuelle", DataWorld(), Returns(true), SudokuSolved).evaluate(fits)._1, GradingStatus.Correct())
    assert(german(grader("fuelle", DataWorld(), SudokuUnchanged).evaluate(fits)).contains("entfernt"))
    val partial = python("def fuelle():\n    Sudoku.set(0, 2, 4)\n")
    assert(german(grader("fuelle", DataWorld(), SudokuSolved).evaluate(partial)).contains("2 Felder"))
    val clash = python("def fuelle():\n    Sudoku.set(0, 2, 3)\n    Sudoku.set(4, 3, 8)\n    Sudoku.set(8, 8, 9)\n")
    assert(german(grader("fuelle", DataWorld(), SudokuSolved).evaluate(clash)).contains("doppelt"))
  }

  test("conversion problems, runtime errors and endless loops are incorrect with an explanation") {
    val unsupported = grader("f", list, ListEquals(Nil)).evaluate(ProgrammingStateRaw(Java, "public class Main { void broken( }"))
    assertEquals(unsupported._1, GradingStatus.Incorrect())
    assert(german(unsupported).startsWith("Das Programm kann so noch nicht ausgeführt werden"))
    val missing = grader("fehlt", list, ListEquals(Nil)).evaluate(bubble)
    assert(german(missing).contains("does not define fehlt"))
    val outside = grader("f", list, ListEquals(Nil)).evaluate(python("def f():\n    NumberList.get(99)\n"))
    assert(german(outside).startsWith("Beim Ausführen trat ein Fehler auf"))
    val endless = ProgramDataTestGrader("loop", description, "f", Nil, list, Seq(ListEquals(Nil)),
      limits = TurtleExecutionLimits(maxLoopIterations = 20)).evaluate(python("def f():\n    while True:\n        NumberList.length()\n"))
    assert(german(endless).contains("Endlosschleife"))
  }

  test("hidden tests report their status without feedback, and grading runs only on request") {
    val hidden = ProgramDataTestGrader("hidden", description, "sortiere", Nil, list, Seq(ListEquals(Nil)), GradingImportance.HIDDEN_TEST)
    hidden.gradeStateSafe(bubble, true, ExecutionContextInformation.default).map { result =>
      assertEquals((result.gradingStatus, result.feedbackInformation), (GradingStatus.Incorrect(), None))
    }.flatMap(_ => hidden.gradeStateSafe(bubble, false, ExecutionContextInformation.default)).map { result =>
      assertEquals(result.gradingStatus, GradingStatus.NotRequested())
    }
  }

  test("graders round-trip through the programming-test codec, including description arguments") {
    val original = ProgramDataTestGrader("sort-0", LanguageMapContentId("basic/sortTestList"), "sortiere", Seq(1, 2),
      DataWorld(list = Vector(3, 1), sudoku = SudokuGrid.default), Seq(ListEquals(Seq(1, 3)), SudokuUnchanged, Returns(true)),
      publicDescriptionArguments = Map("list" -> "[3, 1]"))
    assertEquals(read[ProgrammingTestGrader](write[ProgrammingTestGrader](original)), original)
    assertEquals(readBinary[ProgrammingTestGrader](writeBinary[ProgrammingTestGrader](original)), original)
    intercept[IllegalArgumentException](ProgramDataTestGrader("x", description, "f", Nil, list, Nil))
    intercept[IllegalArgumentException](ProgramDataTestGrader(" x", description, "f", Nil, list, Seq(SudokuSolved)))
  }

  test("sort exercises generate one test per list with the method's rules and readable descriptions") {
    val exercise = SortListExercise("sort", python("def sortiere():\n    pass\n"), "sortiere", testLists = Seq(Seq(3, 1, 2), Nil),
      method = SortingMethod.BubbleSort)
    assertEquals(exercise.sortTests.map(_.testId), Seq("sort-sort-0", "sort-sort-1"))
    assertEquals(exercise.sortTests.map(_.publicDescriptionArguments("list")), Seq("[3, 1, 2]", "[]"))
    assertEquals(exercise.sortTests.head.expectations, Seq(ListEquals(Seq(1, 2, 3)), OnlyAdjacentSwaps, SwapCount(2)))
    assertEquals(exercise.methodExpectations(Seq(2, 1)), Seq(OnlyAdjacentSwaps, SwapCount(1)))
    assertEquals(exercise.copy(method = SortingMethod.SwapsOnly).methodExpectations(Seq(2, 1)), Seq(AvoidsListOperation(ListOperation.Set)))
    assertEquals(exercise.copy(method = SortingMethod.AnyMethod).methodExpectations(Seq(2, 1)), Nil)
    assertEquals(exercise.exampleWorld.list, SortListExercise.defaultExample.toVector)
    assertEquals(exercise.interactionFormat, InteractionFormat.SortListExerciseFormat)
    assertEquals(InteractionFormat.fromName(InteractionFormat.SortListExerciseFormat.name), InteractionFormat.SortListExerciseFormat)
  }

  test("sort exercise tests pass for a correct solution in every allowed method and reject invalid configurations") {
    val exercise = SortListExercise("sort", bubble, "sortiere")
    val statuses = for method <- SortingMethod.values.toSeq; test <- exercise.copy(method = method).sortTests yield test.evaluate(bubble)._1
    assert(statuses.forall(_ == GradingStatus.Correct()), statuses)
    intercept[IllegalArgumentException](exercise.copy(testLists = Nil))
    intercept[IllegalArgumentException](exercise.copy(sortFunction = " "))
    intercept[IllegalArgumentException](exercise.copy(allowedLanguages = Nil))
    // In Snap the number-list blocks call the same functions, so a block solution passes the same tests.
    val blocks = ProgrammingStateRaw(SnapLanguage, bubble.toSnapXml.code)
    val snapStatuses = exercise.copy(allowedLanguages = Seq(SnapLanguage)).sortTests.map(_.evaluate(blocks)._1)
    assert(snapStatuses.forall(_ == GradingStatus.Correct()), snapStatuses)
  }

  test("full Java exercises keep their data view and instruction through serialization") {
    val element = ProgrammingExerciseFullJava("java-data", instructionText = Some(description), dataView = Some(ExerciseDataView.Sudoku))
    val restored = it.evadid.workbook.jsonFactory.WorkbookElementFactory.serializerRefBasedJson.deserialize(
      it.evadid.workbook.jsonFactory.WorkbookElementFactory.serializerRefBasedJson.serialize(element))
    assertEquals(restored, element)
  }
}
