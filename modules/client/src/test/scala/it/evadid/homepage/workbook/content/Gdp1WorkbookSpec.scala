package it.evadid.homepage.workbook.content

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.{SnapLanguage, Python}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.vm.conversion.ProgrammingStateConversions.*
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
  private val advance = "penup()\nright(90)\nforward(200)\nright(270)\npendown()\n"
  private val squares = "for j in range(5):\n" + indent(loop + advance)
  private def indent(code: String): String = code.linesIterator.map("    " + _).mkString("\n") + "\n"
  private val function = "def quadrat():\n" + indent(loop)
  private val withFunction = function + "for j in range(5):\n" + indent("quadrat()\n" + advance)
  private val parameterFunction = "def quadrat(distanz):\n    for i in range(4):\n        forward(distanz)\n        right(90)\n"
  private val parameterAdvance = "penup()\nright(90)\nforward(distanz + 100)\nright(270)\npendown()\n"
  private val increasing = parameterFunction + "distanz = 100\nfor j in range(5):\n" +
    indent("quadrat(distanz)\n" + parameterAdvance + "distanz = distanz + 100\n")
  private val alternating = parameterFunction + "distanz = 100\nfor j in range(5):\n" +
    indent("quadrat(distanz)\n" + parameterAdvance + "if distanz == 100:\n    distanz = 200\nelse:\n    distanz = 100\n")
  private val oriented = "def quadrat(distanz, richtung):\n    for i in range(4):\n        forward(distanz * richtung)\n        right(90 * richtung)\n" +
    "distanz = 100\nfor j in range(5):\n" + indent("if j == 2:\n    quadrat(distanz, -1)\nelse:\n    quadrat(distanz, 1)\n" +
      parameterAdvance + "if distanz == 100:\n    distanz = 200\nelse:\n    distanz = 100\n")
  private val solutions = Seq(square, loop, squares, withFunction, increasing, alternating, oriented)

  tasks.zip(solutions).zipWithIndex.foreach { case ((task, source), index) =>
    test(s"task ${index + 1}: an independently authored solution passes every drawing and structure test") {
      val state = ProgrammingStatePythonString(source)
      Future.traverse(task.gradingElements)(_.gradeStateSafe(state, true, ExecutionContextInformation.default)).map { results =>
        results.foreach(result => assert(result.gradingStatus.isCorrect, clues(task.elementId, result.grader, result.gradingStatus)))
      }
    }
  }

  test("all reference solutions also pass after conversion to Snap blocks") {
    Future.traverse(tasks.zip(solutions)) { (task, source) =>
      val snap = ProgrammingStatePythonString(source).toSnapXml
      Future.traverse(task.gradingElements)(_.gradeStateSafe(snap, true, ExecutionContextInformation.default)).map { results =>
        results.foreach(result => assert(result.gradingStatus.isCorrect, clues(task.elementId, result.grader, result.gradingStatus)))
      }
    }
  }

  test("correct geometry alone cannot bypass the requested structure") {
    val examples = Seq(tasks(1) -> square, tasks(2) -> withFunction, tasks(3) -> withFunction.replace("quadrat", "anderer_name"),
      tasks(4) -> increasing.replace("distanz", "andere_laenge"))
    Future.traverse(examples) { (task, code) =>
      Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStatePythonString(code), true, ExecutionContextInformation.default)).map { results =>
        assert(results.head.gradingStatus.isCorrect, clues(task.elementId, results.head.gradingStatus))
        assert(results.tail.exists(!_.gradingStatus.isCorrect), task.elementId)
      }
    }
  }

  test("palettes progressively enable loops, custom blocks and variables, with Snap and Python only") {
    tasks.zipWithIndex.foreach { (task, index) =>
      assertEquals(task.exerciseConfig.allowedEditorLanguages, Seq(SnapLanguage, Python))
      val tabs = SnapCodeEditorConfig.forPalette(task.exerciseConfig.availablePalette).libraryTabs
      assertEquals(tabs.size, 1)
      assertEquals(tabs.head.includeMakeBlockButton, index >= 3)
      assertEquals(tabs.head.includeVariableControls, index >= 4)
      val selectors = tabs.head.selectableElements.map(_.id)
      assertEquals(selectors.contains("doRepeat"), index >= 1)
      if index == 0 then assertEquals(selectors, Seq("forward", "turn"))
    }
  }

  test("catalogue identity, three sections and reflection grading are configured") {
    assertEquals(workbook.workbookId, "2026GdP1")
    assertEquals(workbook.sections.size, 3)
    assertEquals(workbook.allContainedInteractionsById.size, 8)
    val entry = DigitalWorkbookCatalog.entries.find(_.id == "2026GdP1").get
    assertEquals(entry.native.get.page, "2026GdP1")
    val reflection = workbook.allContainedInteractionsById.values.collectFirst { case text: TextInputWithScaffoldingAndGrading => text }.get
    assertEquals(reflection.contentGrading.size, 1)
    assert(reflection.contentScaffolding.nonEmpty)
  }

  test("structure grading results round-trip through InteractionHistoryJson") {
    val task = tasks(3)
    Future.traverse(task.gradingElements)(_.gradeStateSafe(ProgrammingStatePythonString(withFunction), true, ExecutionContextInformation.default)).map { results =>
      val history = InteractionVariableHistory.empty[TurtleRecreateShapeInteractionFormat.type].addGradings(results)
      val envelope = InteractionHistoryJson.fromHistory(TurtleRecreateShapeInteractionFormat, history)
      val restored = read[InteractionHistoryJson](write(envelope))
      assertEquals(restored, envelope)
      assertEquals(restored.typedAs(TurtleRecreateShapeInteractionFormat), history)
    }
  }
}
