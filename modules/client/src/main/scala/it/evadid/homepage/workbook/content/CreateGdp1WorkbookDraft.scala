package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.*
import it.evadid.workbook.interactions.programming.state.{ProgrammingStatePythonStructured, ProgrammingStateSnapScalaModel}
import it.evadid.workbook.interactions.programming.state.snap.{PaletteLayout, ProgrammingEditorPalette}
import it.evadid.workbook.elements.structure.Workbook
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.{DataExpectation, ExerciseDataView, ListOperation, ProgramDataTestGrader, ProgrammingExerciseFullJava, SortListExercise, SortingMethod}
import it.evadid.vm.test.ProgrammingTestSuite
import it.evadid.vm.simulation.data.{DataWorld, SudokuGrid}

/** Progressive practice: explicit commands, loops, functions, then parameters. */
case class CreateGdp1Workbook(override val fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "2026GdP1"
  override val availableLanguages: Seq[HumanLanguage] = Seq(German, English)
  private def key(name: String) = s"2026gdp1/$name"
  private def content(name: String) = LanguageMapContentId(key(name))
  private val movement = Seq("receiveGo", "forward", "turn")
  private val beginner = Seq("receiveGo", "forward", "turn", "up", "down", "doRepeat", "doIfElse", "reportVariadicSum", "reportDifference", "reportVariadicProduct", "reportVariadicEquals")

  private def palette(selectors: Seq[String], functions: Boolean = false, variables: Boolean = false) =
    ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(selectors, functions, variables))

  private def task(number: Int, target: TurtleGraphic, available: ProgrammingEditorPalette,
      requirements: Seq[(ProgramStructureRequirement, String)]): WorkbookElement = {
    val id = s"$workbookId-task-$number"
    val tests = requirements.zipWithIndex.map { case ((requirement, description), index) =>
      ProgramStructureGrader(requirement, s"$id-structure-$index", content(description))
    }
    container(key(s"task${number}Title"), Seq(
      instructionMarkdown(key(s"task${number}Instruction")),
      TurtleRecreateShapeInteraction(id, ProgrammingStateSnapScalaModel.empty, target,
        customExerciseConfig = Some(RecreateTurtleDidacticConfig(Seq(SnapLanguage, Python, Java), available, MaxCommandUsage.noBounds)),
        structureTests = tests, instructionText = Some(content(s"task${number}Instruction")))))
  }

  import ProgramStructureRequirement.*
  private val repetition = UsesLoop -> "testLoop"
  private val noFunctions = NoFunctions -> "testNoFunctions"
  private val squareFunction = DefinesFunction("quadrat") -> "testSquareFunction"

  override lazy val createWorkbook: Workbook = workbook(key("title"), Seq(
    section(s"$workbookId-overview", key("section0Title"), Seq(instructionMarkdown(key("overview")))),
    section(s"$workbookId-introduction", key("section1Title"), Seq(
      instructionMarkdown(key("turtleIntroduction")),
      task(1, CreateGdp1Workbook.square, palette(movement), Seq(NoLoops -> "testNoLoops", noFunctions, OnlyCommands(Seq("forward", "right")) -> "testOnlyCommands")),
      instructionMarkdown(key("loopIntroduction")),
      task(2, CreateGdp1Workbook.square, palette(movement :+ "doRepeat"), Seq(repetition, noFunctions)),
      instructionMarkdown(key("nestedLoopsIntroduction")),
      task(3, CreateGdp1Workbook.row(Seq.fill(5)(100)), palette(beginner), Seq(repetition, noFunctions)),
      instructionMarkdown(key("functionIntroduction")),
      task(4, CreateGdp1Workbook.row(Seq.fill(5)(100)), palette(beginner, functions = true), Seq(repetition, squareFunction)),
      container(key("reflectionTitle"), Seq(TextInputWithScaffoldingAndGrading(s"$workbookId-reflection", content("reflectionInstruction"),
        contentScaffolding = Some(ContentForLlmInteraction(content("reflectionScaffolding"), Map.empty)),
        contentGrading = Seq(ContentForLlmInteraction(content("reflectionGrading"), Map.empty)))))
    )),
    section(s"$workbookId-parameters", key("section2Title"), Seq(
      instructionMarkdown(key("parameterIntroduction")),
      task(5, CreateGdp1Workbook.row(Seq(100, 200, 300, 400, 500)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter")),
      instructionMarkdown(key("alternationIntroduction")),
      task(6, CreateGdp1Workbook.row(Seq(100, 200, 100, 200, 100)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter")),
      instructionMarkdown(key("orientationIntroduction")),
      task(7, CreateGdp1Workbook.row(Seq(100, 200, 100, 200, 100), Set(2)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter"))
    )),
    section(s"$workbookId-java", key("section3Title"), Seq(
      instructionMarkdown(key("javaIntroduction")),
      javaTask("swap", CreateGdp1Workbook.swapProgram, ExerciseDataView.NumberList, CreateGdp1Workbook.swapTests),
      instructionMarkdown(key("bubbleIntroduction")),
      javaTask("bubble", CreateGdp1Workbook.bubbleProgram, ExerciseDataView.NumberList, CreateGdp1Workbook.bubbleTests),
      instructionMarkdown(key("sudokuIntroduction")),
      javaTask("sudoku", CreateGdp1Workbook.sudokuProgram, ExerciseDataView.Sudoku, CreateGdp1Workbook.sudokuTests),
      instructionMarkdown(key("sortIntroduction")),
      container(key("sortTitle"), Seq(
        instructionMarkdown(key("sortInstruction")),
        SortListExercise(s"$workbookId-sort", ProgrammingStatePythonStructured(CreateGdp1Workbook.sortProgram), "sortiere",
          method = SortingMethod.SwapsOnly, allowedLanguages = Seq(SnapLanguage, Python, Java), instructionText = Some(content("sortInstruction")))))
    ))), User.AndreGreubel)

  /** A full Java exercise whose unit tests run on the shared interpreter against the provided list or sudoku. */
  private def javaTask(name: String, program: String, view: ExerciseDataView,
      tests: Seq[CreateGdp1Workbook.DataTest]): WorkbookElement = {
    val id = s"$workbookId-java-$name"
    val graders = tests.zipWithIndex.map { case (test, index) =>
      ProgramDataTestGrader(s"$id-test-$index", content(test.description), test.function, test.arguments, test.world, test.expectations)
    }
    container(key(s"${name}Title"), Seq(
      instructionMarkdown(key(s"${name}Instruction")),
      ProgrammingExerciseFullJava(id, Some(ProgrammingTestSuite(graders.toList)), program,
        hintTask = Some(content(s"${name}Instruction")), instructionText = Some(content(s"${name}Instruction")), dataView = Some(view))))
  }
}

object CreateGdp1Workbook {
  import TurtleCommand.*
  /** The turtle starts at the shared line facing right, as in Snap and Python. Right turns
    * draw a square hanging below that line; mirrored turns place it above the line.
    */
  private def squareCommands(side: Int, above: Boolean = false): Seq[TurtleCommand[Double]] =
    Seq.fill(4)(Seq(Forward(side.toDouble), Right(if above then -90.0 else 90.0))).flatten
  private[content] val square: TurtleGraphic = TurtleGraphic(squareCommands(100))

  /** A horizontal row whose squares share their top or bottom edge line. Gaps are measured
    * between neighboring square edges; pen-up travel is not graded as a stroke.
    */
  private[content] def row(sides: Seq[Int], above: Set[Int] = Set.empty): TurtleGraphic = TurtleGraphic(
    sides.zipWithIndex.flatMap { (side, index) =>
      squareCommands(side, above(index)) ++ Option.when(index < sides.size - 1)(Seq(
        PenUp, Forward(side + 100.0), PenDown)).toSeq.flatten
    })

  // Java section: the list and sudoku are provided implicitly; the static methods below are their interface.

  final case class DataTest(description: String, function: String, arguments: Seq[Double], world: DataWorld,
      expectations: Seq[DataExpectation])

  private val listAccess =
    """    // Vorgegeben: Zugriff auf die Zahlenliste (Positionen ab 0)
      |    static int getWert(int position) {
      |        return NumberList.get(position);
      |    }
      |
      |    static void setWert(int position, int wert) {
      |        NumberList.set(position, wert);
      |    }
      |
      |    static int laenge() {
      |        return NumberList.length();
      |    }
      |""".stripMargin

  private[content] val swapProgram: String =
    s"""public class Main {
       |$listAccess
       |    // Deine Aufgabe: Vertausche die Werte an den Positionen i und j.
       |    static void tausche(int i, int j) {
       |    }
       |
       |    public static void main(String[] args) {
       |        tausche(0, 1);
       |    }
       |}
       |""".stripMargin

  private[content] val bubbleProgram: String =
    s"""public class Main {
       |$listAccess
       |    // Vorgegeben: vertauscht die Werte an den Positionen i und j
       |    static void tausche(int i, int j) {
       |        NumberList.swap(i, j);
       |    }
       |
       |    // Deine Aufgabe: Sortiere die Liste aufsteigend mit Bubble Sort.
       |    static void bubbleSort() {
       |    }
       |
       |    public static void main(String[] args) {
       |        bubbleSort();
       |    }
       |}
       |""".stripMargin

  private[content] val sudokuProgram: String =
    """public class Main {
      |    // Vorgegeben: Zugriff auf das Sudoku (Zeilen und Spalten 0 bis 8, 0 = leeres Feld)
      |    static int getFeld(int zeile, int spalte) {
      |        return Sudoku.get(zeile, spalte);
      |    }
      |
      |    static void setzeFeld(int zeile, int spalte, int zahl) {
      |        Sudoku.set(zeile, spalte, zahl);
      |    }
      |
      |    static void entferneFeld(int zeile, int spalte) {
      |        Sudoku.remove(zeile, spalte);
      |    }
      |
      |    // Deine Aufgabe 1: Darf zahl in dieses Feld, ohne Zeile, Spalte oder 3x3-Block zu verletzen?
      |    static boolean passt(int zeile, int spalte, int zahl) {
      |        return false;
      |    }
      |
      |    // Deine Aufgabe 2: Fülle alle leeren Felder (Backtracking); true, wenn das gelingt.
      |    static boolean loese() {
      |        return false;
      |    }
      |
      |    public static void main(String[] args) {
      |        loese();
      |    }
      |}
      |""".stripMargin

  private[content] val sortProgram: String =
    """def sortiere():
      |    # NumberList.length(), NumberList.get(i) und NumberList.swap(i, j) stehen zur Verfügung.
      |    pass
      |
      |sortiere()
      |""".stripMargin

  import DataExpectation.*
  private def list(values: Int*) = DataWorld(list = values.toVector)
  private def sortedTest(description: String, values: Int*) =
    DataTest(description, "bubbleSort", Nil, list(values *), Seq(ListEquals(values.sorted)))

  private[content] val swapTests: Seq[DataTest] = Seq(
    DataTest("testSwapMiddle", "tausche", Seq(1, 3), list(4, 8, 15, 16, 23), Seq(ListEquals(Seq(4, 16, 15, 8, 23)))),
    DataTest("testSwapEnds", "tausche", Seq(0, 4), list(4, 8, 15, 16, 23), Seq(ListEquals(Seq(23, 8, 15, 16, 4)))),
    DataTest("testSwapSame", "tausche", Seq(2, 2), list(4, 8, 15, 16, 23), Seq(ListEquals(Seq(4, 8, 15, 16, 23)))),
    DataTest("testSwapOwnCode", "tausche", Seq(0, 1), list(1, 2), Seq(UsesListOperation(ListOperation.Set),
      AvoidsListOperation(ListOperation.Swap))))

  private[content] val bubbleTests: Seq[DataTest] = Seq(
    sortedTest("testBubbleExample", 7, 3, 9, 1, 5),
    sortedTest("testBubbleDescending", 6, 5, 4, 3, 2, 1),
    sortedTest("testBubbleDuplicates", 3, 1, 3, 2, 1),
    DataTest("testBubbleSorted", "bubbleSort", Nil, list(1, 2, 3, 4), Seq(ListEquals(Seq(1, 2, 3, 4)), SwapCount(0))),
    DataTest("testBubbleShort", "bubbleSort", Nil, list(42), Seq(ListEquals(Seq(42)))),
    DataTest("testBubbleMethod", "bubbleSort", Nil, list(5, 1, 4, 2, 8), Seq(ListEquals(Seq(1, 2, 4, 5, 8)),
      OnlyAdjacentSwaps, SwapCount(ProgramDataTestGrader.inversions(Seq(5, 1, 4, 2, 8))))))

  /** Sparse grid for `passt`: 4 at (0,0), 2 at (0,4), 9 at (3,3), 7 at (4,0), 3 at (6,6). */
  private val checkGrid = DataWorld(sudoku = SudokuGrid.parse(
    "4...2....", ".........", ".........", "...9.....", "7........", ".........", "......3..", ".........", "........."))

  /** A classic solution with 17 cells removed. */
  private val mediumSudoku = DataWorld(sudoku = SudokuGrid.parse(
    "5.467.91.", "672.95.48", ".98342.67", "8597.1423", "42.853.91", "7139.48.6", "96.537.84", "2874.9635", ".45.86179"))

  /** 5 fits at (0,0), but (8,8) needs 9, which its column already holds: a solver must place, fail and remove again. */
  private val unsolvableSudoku = DataWorld(sudoku = SudokuGrid.parse(
    ".34678912", "672195348", "198342567", "859761423", "426853791", "713924856", "961537284", "287419639", "34528617."))

  private[content] val sudokuTests: Seq[DataTest] = Seq(
    DataTest("testFitsRow", "passt", Seq(4, 4, 7), checkGrid, Seq(Returns(false))),
    DataTest("testFitsColumn", "passt", Seq(4, 4, 2), checkGrid, Seq(Returns(false))),
    DataTest("testFitsBlock", "passt", Seq(4, 4, 9), checkGrid, Seq(Returns(false))),
    DataTest("testFitsOtherBlock", "passt", Seq(7, 8, 3), checkGrid, Seq(Returns(false))),
    DataTest("testFitsFree", "passt", Seq(4, 4, 4), checkGrid, Seq(Returns(true))),
    DataTest("testSolveExample", "loese", Nil, DataWorld(), Seq(Returns(true), SudokuSolved)),
    DataTest("testSolveMedium", "loese", Nil, mediumSudoku, Seq(Returns(true), SudokuSolved)),
    DataTest("testSolveImpossible", "loese", Nil, unsolvableSudoku, Seq(Returns(false), SudokuUnchanged)))
}
