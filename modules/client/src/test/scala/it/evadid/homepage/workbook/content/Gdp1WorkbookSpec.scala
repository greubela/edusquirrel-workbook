package it.evadid.homepage.workbook.content

import it.evadid.workbook.interaction.sync.UpdateImportance

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.{SnapLanguage, Python, Java}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStatePythonStructured, ProgrammingStateRaw}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ExerciseDataView, ProgramDataTestGrader, ProgrammingExerciseFullJava, SortListExercise}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.abstractions.InteractionFormat.TurtleRecreateShapeInteractionFormat
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

class Gdp1WorkbookSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val workbook = CreateGdp1Workbook(null).createWorkbook
  private val tasks = (1 to 7).map(number => workbook.allContainedInteractionsById(s"2026GdP1-task-$number")
    match { case task: TurtleRecreateShapeInteraction => task; case other => fail(s"Unexpected task: $other") })
  private val square = "forward(100)\nright(90)\n" * 4
  private val loop = "for i in range(4):\n    forward(100)\n    right(90)\n"
  private val advance = "penup()\nforward(200)\npendown()\n"
  private val squares = "for j in range(5):\n" + indent(loop + advance)
  private def indent(code: String): String = code.linesIterator.map("    " + _).mkString("\n") + "\n"
  private val function = "def quadrat():\n" + indent(loop)
  private val withFunction = function + "for j in range(5):\n" + indent("quadrat()\n" + advance)
  private val parameterFunction = "def quadrat(distanz):\n    for i in range(4):\n        forward(distanz)\n        right(90)\n"
  private val parameterAdvance = "penup()\nforward(distanz + 100)\npendown()\n"
  private val increasing = parameterFunction + "distanz = 100\nfor j in range(5):\n" +
    indent("quadrat(distanz)\n" + parameterAdvance + "distanz = distanz + 100\n")
  private val alternating = parameterFunction + "distanz = 100\nfor j in range(5):\n" +
    indent("quadrat(distanz)\n" + parameterAdvance + "if distanz == 100:\n    distanz = 200\nelse:\n    distanz = 100\n")
  private val oriented = "def quadrat(distanz, richtung):\n    for i in range(4):\n        forward(distanz)\n        right(90 * richtung)\n" +
    "distanz = 100\nfor j in range(5):\n" + indent("if j == 2:\n    quadrat(distanz, -1)\nelse:\n    quadrat(distanz, 1)\n" +
      parameterAdvance + "if distanz == 100:\n    distanz = 200\nelse:\n    distanz = 100\n")
  private val solutions = Seq(square, loop, squares, withFunction, increasing, alternating, oriented)

  tasks.zip(solutions).zipWithIndex.foreach { case ((task, source), index) =>
    test(s"task ${index + 1}: an independently authored solution passes every drawing and structure test") {
      val state = ProgrammingStatePythonStructured(source)
      Future.traverse(task.gradingElements)(_.gradeStateSafe(state, true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
        results.foreach(result => assert(result.gradingStatus.isCorrect, clues(task.elementId, result.grader, result.gradingStatus)))
      }
    }
  }

  test("row targets run horizontally and match the described orientation") {
    // Screen (SVG) coordinates: y grows downward, so squares below the shared line have y >= 0.
    def boxes(graphic: TurtleGraphic) = graphic.renderLines.grouped(4).map { lines =>
      val points = lines.flatMap(line => Seq(line.start, line.end))
      (points.map(_.x).min, points.map(_.x).max, points.map(_.y).min, points.map(_.y).max)
    }.toSeq
    val sizes = Seq(100, 200, 100, 200, 100)
    val starts = sizes.scanLeft(0)((x, side) => x + side + 100).init
    val expected = sizes.zip(starts).zipWithIndex.map { case ((side, x), index) =>
      if index == 2 then (x.toDouble, (x + side).toDouble, -side.toDouble, 0.0) else (x.toDouble, (x + side).toDouble, 0.0, side.toDouble)
    }
    boxes(tasks(6).desiredResult).zip(expected).foreach { (actual, wanted) =>
      Seq(actual._1 - wanted._1, actual._2 - wanted._2, actual._3 - wanted._3, actual._4 - wanted._4)
        .foreach(delta => assert(math.abs(delta) < 1e-6, clues(actual, wanted)))
    }
    assertEquals(boxes(tasks(2).desiredResult).map(box => math.round(box._3)).distinct, Seq(0L))
  }

  test("all reference solutions also pass after conversion to Snap blocks") {
    Future.traverse(tasks.zip(solutions)) { (task, source) =>
      val snap = ProgrammingStatePythonStructured(source).toSnapXml
      Future.traverse(task.gradingElements)(_.gradeStateSafe(snap, true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
        results.foreach(result => assert(result.gradingStatus.isCorrect, clues(task.elementId, result.grader, result.gradingStatus)))
      }
    }
  }

  test("all reference solutions pass as structured Java programs using the shared interpreter") {
    Future.traverse(tasks.zip(solutions)) { (task, source) =>
      val java = ProgrammingStatePythonStructured(source).toJavaStructured
      Future.traverse(task.gradingElements)(_.gradeStateSafe(java, true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
        results.foreach(result => assert(result.gradingStatus.isCorrect, clues(task.elementId, java, result.grader, result.gradingStatus)))
      }
    }
  }

  test("editor execution and grading share the BeExpression drawing for all languages") {
    val execution = new it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleProgramExecution(sharedInterpreter = true)
    val python = ProgrammingStatePythonStructured(oriented)
    val expected = python.toBeExpression.turtleCommandExecutionInfo.completedCommands
    Future.traverse(Seq(python, python.toSnapXml, python.toJavaStructured)) { source =>
      execution.commandsFor(source).map(commands => assertEquals(commands, expected))
    }.andThen { case _ => execution.close() }
  }

  test("correct geometry alone cannot bypass the requested structure") {
    val examples = Seq(tasks(1) -> square, tasks(2) -> withFunction, tasks(3) -> withFunction.replace("quadrat", "anderer_name"),
      tasks(4) -> increasing.replace("distanz", "andere_laenge"))
    Future.traverse(examples) { (task, code) =>
      Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStatePythonStructured(code), true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
        assert(results.head.gradingStatus.isCorrect, clues(task.elementId, results.head.gradingStatus))
        assert(results.tail.exists(!_.gradingStatus.isCorrect), task.elementId)
      }
    }
  }

  test("palettes progressively enable loops, custom blocks and variables, with Snap, Python and Java") {
    tasks.zipWithIndex.foreach { (task, index) =>
      assertEquals(task.exerciseConfig.allowedEditorLanguages, Seq(SnapLanguage, Python, Java))
      val tabs = SnapCodeEditorConfig.forPalette(task.exerciseConfig.availablePalette).libraryTabs
      assertEquals(tabs.size, 1)
      assertEquals(tabs.head.includeMakeBlockButton, index >= 3)
      assertEquals(tabs.head.includeVariableControls, index >= 4)
      val selectors = tabs.head.selectableElements.map(_.id)
      assertEquals(selectors.contains("doRepeat"), index >= 1)
      if index == 0 then assertEquals(selectors, Seq("receiveGo", "forward", "turn"))
    }
  }

  // Java section: reference solutions replace the task stubs of the starting programs.
  private def javaTask(name: String) = workbook.allContainedInteractionsById(s"2026GdP1-java-$name") match {
    case task: ProgrammingExerciseFullJava => task
    case other => fail(s"Unexpected Java task: $other")
  }
  private def solved(program: String, stubs: (String, String)*): String = stubs.foldLeft(program) { case (source, (stub, body)) =>
    assert(source.contains(stub), stub); source.replace(stub, body)
  }
  private val swapSolution = solved(CreateGdp1Workbook.swapProgram,
    "static void tausche(int i, int j) {\n    }" ->
      "static void tausche(int i, int j) {\n        int merke = getWert(i);\n        setWert(i, getWert(j));\n        setWert(j, merke);\n    }")
  private val bubbleLoop =
    """static void bubbleSort() {
      |        for (int durchgang = 0; durchgang < laenge(); durchgang++) {
      |            for (int i = 0; i < laenge() - 1 - durchgang; i++) {
      |                if (getWert(i) > getWert(i + 1)) {
      |                    tausche(i, i + 1);
      |                }
      |            }
      |        }
      |    }""".stripMargin
  private val bubbleSolution = solved(CreateGdp1Workbook.bubbleProgram, "static void bubbleSort() {\n    }" -> bubbleLoop)
  private val fitsBody =
    """static boolean passt(int zeile, int spalte, int zahl) {
      |        for (int k = 0; k < 9; k++) {
      |            if (getFeld(zeile, k) == zahl || getFeld(k, spalte) == zahl) {
      |                return false;
      |            }
      |        }
      |        int startZeile = zeile - zeile % 3;
      |        int startSpalte = spalte - spalte % 3;
      |        for (int z = startZeile; z < startZeile + 3; z++) {
      |            for (int s = startSpalte; s < startSpalte + 3; s++) {
      |                if (getFeld(z, s) == zahl) {
      |                    return false;
      |                }
      |            }
      |        }
      |        return true;
      |    }""".stripMargin
  private val solveBody =
    """static boolean loese() {
      |        for (int z = 0; z < 9; z++) {
      |            for (int s = 0; s < 9; s++) {
      |                if (getFeld(z, s) == 0) {
      |                    for (int zahl = 1; zahl <= 9; zahl++) {
      |                        if (passt(z, s, zahl)) {
      |                            setzeFeld(z, s, zahl);
      |                            if (loese()) {
      |                                return true;
      |                            }
      |                            entferneFeld(z, s);
      |                        }
      |                    }
      |                    return false;
      |                }
      |            }
      |        }
      |        return true;
      |    }""".stripMargin
  private val fitsStub = "static boolean passt(int zeile, int spalte, int zahl) {\n        return false;\n    }"
  private val solveStub = "static boolean loese() {\n        return false;\n    }"
  private val sudokuSolution = solved(CreateGdp1Workbook.sudokuProgram, fitsStub -> fitsBody, solveStub -> solveBody)

  private def failedTests(task: ProgrammingExerciseFullJava, source: String): Future[Seq[String]] =
    Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStateJavaStructured(source), true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
      results.foreach(result => assert(result.gradingStatus.isCorrect || result.gradingStatus == GradingStatus.Incorrect(),
        clues(task.elementId, result.grader, result.gradingStatus, result.feedbackInformation)))
      results.filterNot(_.gradingStatus.isCorrect).map(_.grader.asInstanceOf[ProgramDataTestGrader].publicDescription.entryKey)
    }

  test("Java section: reference solutions pass every unit test, as structured and as raw Java") {
    val cases = Seq("swap" -> swapSolution, "bubble" -> bubbleSolution, "sudoku" -> sudokuSolution)
    Future.traverse(cases) { (name, source) =>
      val task = javaTask(name)
      failedTests(task, source).map(failed => assertEquals(failed, Nil, name)).flatMap { _ =>
        Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStateRaw(Java, source), true, UpdateImportance.MAJOR, ExecutionContextInformation.default))
          .map(results => assert(results.forall(_.gradingStatus.isCorrect), name))
      }
    }
  }

  test("Java section: starting programs fail with explanations instead of execution errors") {
    Future.traverse(Seq("swap", "bubble", "sudoku")) { name =>
      val task = javaTask(name)
      Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStateJavaStructured(task.startingProgram), true, UpdateImportance.MAJOR, ExecutionContextInformation.default))
        .map { results =>
          assert(results.exists(_ .gradingStatus == GradingStatus.Incorrect()), name)
          assert(results.forall(result => result.gradingStatus.isCorrect || result.feedbackInformation.nonEmpty), name)
        }
    }
  }

  test("Java section: typical wrong solutions are caught by the intended tests") {
    val readyMadeSwap = solved(CreateGdp1Workbook.swapProgram,
      "static void tausche(int i, int j) {\n    }" -> "static void tausche(int i, int j) {\n        NumberList.swap(i, j);\n    }")
    val forgetful = solved(CreateGdp1Workbook.swapProgram,
      "static void tausche(int i, int j) {\n    }" -> "static void tausche(int i, int j) {\n        setWert(i, getWert(j));\n        setWert(j, getWert(i));\n    }")
    val selectionSort = solved(CreateGdp1Workbook.bubbleProgram, "static void bubbleSort() {\n    }" ->
      """static void bubbleSort() {
        |        for (int i = 0; i < laenge(); i++) {
        |            int kleinste = i;
        |            for (int j = i + 1; j < laenge(); j++) {
        |                if (getWert(j) < getWert(kleinste)) {
        |                    kleinste = j;
        |                }
        |            }
        |            tausche(i, kleinste);
        |        }
        |    }""".stripMargin)
    val noBlock = solved(CreateGdp1Workbook.sudokuProgram, fitsStub -> fitsBody.replace("getFeld(z, s) == zahl", "false"), solveStub -> solveBody)
    val noUndo = solved(CreateGdp1Workbook.sudokuProgram, fitsStub -> fitsBody, solveStub -> solveBody.replace("entferneFeld(z, s);", ""))
    def ids(failed: Seq[String]) = failed
    for {
      swapFailures <- failedTests(javaTask("swap"), readyMadeSwap)
      forgetfulFailures <- failedTests(javaTask("swap"), forgetful)
      bubbleFailures <- failedTests(javaTask("bubble"), selectionSort)
      blockFailures <- failedTests(javaTask("sudoku"), noBlock)
      undoFailures <- failedTests(javaTask("sudoku"), noUndo)
    } yield {
      assertEquals(ids(swapFailures), Seq("testswapowncode"))
      assert(ids(forgetfulFailures).contains("testswapmiddle"))
      assert(ids(bubbleFailures).contains("testbubblemethod"), bubbleFailures)
      assert(Set("testfitsblock", "testfitsotherblock").subsetOf(ids(blockFailures).toSet), blockFailures)
      assertEquals(ids(undoFailures), Seq("testsolveimpossible"))
    }
  }

  test("Java section: exercises show their data and instruction beside the editor") {
    assertEquals(Seq("swap", "bubble", "sudoku").map(javaTask(_).dataView),
      Seq(Some(ExerciseDataView.NumberList), Some(ExerciseDataView.NumberList), Some(ExerciseDataView.Sudoku)))
    assert(Seq("swap", "bubble", "sudoku").forall(javaTask(_).instructionText.nonEmpty))
  }

  private val sortTask = workbook.allContainedInteractionsById("2026GdP1-sort") match {
    case task: SortListExercise => task
    case other => fail(s"Unexpected sort task: $other")
  }
  private val selectionSort =
    """def sortiere():
      |    n = NumberList.length()
      |    for i in range(n):
      |        kleinste = i
      |        for j in range(i + 1, n):
      |            if NumberList.get(j) < NumberList.get(kleinste):
      |                kleinste = j
      |        NumberList.swap(i, kleinste)
      |
      |sortiere()
      |""".stripMargin

  private def sortResults(state: it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState) =
    Future.traverse(sortTask.gradingElements)(_.gradeStateSafe(state, true, UpdateImportance.MAJOR, ExecutionContextInformation.default))

  test("sort exercise: a swap-based solution passes every generated test in Python and after conversion to Java and Snap") {
    assertEquals(sortTask.sortTests.size, SortListExercise.defaultTestLists.size)
    assertEquals(sortTask.allowedLanguages, Seq(SnapLanguage, Python, Java))
    val python = ProgrammingStatePythonStructured(selectionSort)
    val blocks = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(SnapLanguage, python.toSnapXml.code)
    for {
      inPython <- sortResults(python)
      inJava <- sortResults(python.toJavaStructured)
      inSnap <- sortResults(blocks)
    } yield {
      assert(inPython.forall(_.gradingStatus.isCorrect), inPython.map(r => (r.grader, r.gradingStatus, r.feedbackInformation)))
      assert(inJava.forall(_.gradingStatus.isCorrect), python.toJavaStructured.toString)
      assert(inSnap.forall(_.gradingStatus.isCorrect), inSnap.map(r => (r.gradingStatus, r.feedbackInformation)))
    }
  }

  test("sort exercise: the starting program fails and moving values with set breaks the swaps-only rule") {
    val bySet = selectionSort.replace("        NumberList.swap(i, kleinste)",
      "        merke = NumberList.get(i)\n        NumberList.set(i, NumberList.get(kleinste))\n        NumberList.set(kleinste, merke)")
    for {
      start <- sortResults(ProgrammingStatePythonStructured(sortTask.initProgram.toString))
      set <- sortResults(ProgrammingStatePythonStructured(bySet))
    } yield {
      assert(start.exists(!_.gradingStatus.isCorrect))
      val unsorted = set.find(_.grader.asInstanceOf[ProgramDataTestGrader].publicDescriptionArguments("list") == "[5, 1, 4, 2, 8]").get
      assertEquals(unsorted.gradingStatus, GradingStatus.Incorrect())
      assert(unsorted.feedbackInformation.get.toString.contains("NumberList.set"), unsorted.feedbackInformation)
    }
  }

  test("catalogue identity, four sections and reflection grading are configured") {
    assertEquals(workbook.workbookId, "2026GdP1")
    assertEquals(workbook.sections.size, 4)
    assertEquals(workbook.allContainedInteractionsById.size, 12)
    val entry = DigitalWorkbookCatalog.entries.find(_.id == "2026GdP1").get
    assertEquals(entry.native.get.page, "2026GdP1")
    val reflection = workbook.allContainedInteractionsById.values.collectFirst { case text: TextInputWithScaffoldingAndGrading => text }.get
    assertEquals(reflection.contentGrading.size, 1)
    assert(reflection.contentScaffolding.nonEmpty)
  }

  test("structure grading results round-trip through InteractionHistoryJson") {
    val task = tasks(3)
    Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStatePythonStructured(withFunction), true, UpdateImportance.MAJOR, ExecutionContextInformation.default)).map { results =>
      val history = InteractionVariableHistory.empty[TurtleRecreateShapeInteractionFormat.type].addGradings(results)
      val envelope = InteractionHistoryJson.fromHistory(TurtleRecreateShapeInteractionFormat, history)
      val restored = read[InteractionHistoryJson](write(envelope))
      assertEquals(restored, envelope)
      assertEquals(restored.typedAs(TurtleRecreateShapeInteractionFormat), history)
    }
  }
}
