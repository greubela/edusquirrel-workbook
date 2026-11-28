package it.evadid.workbook.interactions.programming

import it.evadid.workbook.support.OfflineController

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.pythonsnap.PythonToSnap
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.interactions.programming.state.*
import it.evadid.workbook.interactions.programming.state.snap.SnapScalaModel.BlockDefinition
import it.evadid.workbook.interactions.programming.state.snap.SnapVocabulary.BlockType
import it.evadid.workbook.sync.UpdateImportance.MAJOR
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** A turtle exercise answered with functions that return values: graded alike in every language, and kept
  * through Snap's write-back of an edited Python program.
  */
class ReporterExerciseSpec extends FunSuite {
  private val square = TurtleGraphic.TurtleGraphicProgram(
    ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(20)\n    left(90)\n").toBeExpression.turtleCommandExecutionInfo.completedCommands)
  private val reporters = "def side(n: float) -> float:\n    return n * 4\n\ndef more(i: float) -> bool:\n    return i < 4\n\n" +
    "i = 0\nwhile more(i):\n    forward(side(5))\n    left(90)\n    i = i + 1\n"
  private val answer = ProgrammingStateRaw(Python, reporters)

  private def grade(source: ProgrammingState): Future[GradingStatus] =
    RecreateShapeGrader(square, GradingImportance.REQUIRED_TEST).gradeStateSafe(source, true, MAJOR, TestExecutionContext()).map(_.gradingStatus)

  test("an answer built from reporters and predicates grades correct in Python, Java and Snap") {
    val sources = List[ProgrammingState](answer, answer.toJava, answer.toSnapXml, answer.toSnapScalaModel.toOption.get,
      answer.toSnapXml.convertToLanguage(Python).finalState.get)
    Future.traverse(sources)(source => grade(source).map(source.associatedLanguage -> _)).map { results =>
      assertEquals(results.map(_._2), List.fill(sources.size)(GradingStatus.Correct), results.toString)
    }
  }

  test("a reporter returning the wrong value grades incorrect, also after the Snap round trip") {
    val wrong = ProgrammingStateRaw(Python, reporters.replace("n * 4", "n * 3"))
    Future.traverse(List[ProgrammingState](wrong, wrong.toSnapXml, wrong.toJava))(grade).map { results =>
      assert(results.forall(_ != GradingStatus.Correct), results.toString)
    }
  }

  test("the exercise interaction grades a student's reporter answer from its history") {
    val interaction = TurtleRecreateShapeInteraction("reporter-square", ProgrammingStateRaw(Python, "pass\n"), square)
    val answers = OfflineController.of(interaction)
    answers.setStateFromUserInteraction(interaction, answer.toSnapXml, MAJOR)
    // The edit's automatic grading skips the shape grader; the requested one after it runs it.
    answers.feedback(interaction).getOrCalculateGrading().flatMap(_ =>
      answers.feedback(interaction).getOrCalculateGrading(forceGrading = true)).map { graded =>
      assertEquals(graded.testsWithResults.head.gradingStatus, GradingStatus.Correct)
    }
  }

  test("editing a reporter's body in Python keeps the authored Snap definition, its labels and its calls") {
    val authored = ProgrammingStateRaw(SnapLanguage,
      """<project name="authored" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><scenes select="1"><scene name="authored">""" +
        """<blocks><block-definition s="side length of %'n'" type="reporter" category="operators"><header></header><code></code>""" +
        """<translations></translations><inputs><input type="%n"></input></inputs><script><block s="doReport"><block s="reportVariadicProduct">""" +
        """<list><block var="n"/><l>2</l></list></block></block></script></block-definition></blocks><stage name="Stage"><sprites select="1">""" +
        """<sprite name="Turtle"><scripts><script x="10" y="10"><block s="receiveGo"></block><block s="forward"><custom-block s="side length of %n">""" +
        """<l>5</l></custom-block></block></script></scripts></sprite></sprites></stage></scene></scenes></project>""")
    val python = authored.convertToLanguage(Python).finalState.get.toString
    assert(python.contains("def side_length_of(n: float) -> float:"), python)
    val edited = PythonToSnap.replacing(authored, python.replace("n * 2", "n * 4")).fold(fail(_), identity)
    val model = edited.toSnapScalaModel.fold(failure => fail(failure.message), _.model)
    val definitions = (model.root +: model.root.descendants).collect { case d: BlockDefinition => (d.spec, d.blockType, d.category.map(_.xml)) }
    assertEquals(definitions, Vector((Some("side length of %'n'"), Some(BlockType.Reporter), Some("operators"))),
      "the authored label words, quoted slot name, type and category survive")
    assertEquals(SnapCustomBlockRules.obsoleteCalls(edited.code), Nil)
    assertEquals(edited.toBeExpression.turtleCommandExecutionInfo.completedCommands,
      ProgrammingStateRaw(Python, "forward(20)\n").toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }

  test("turning a command into a reporter in Python changes the Snap block type") {
    val command = PythonToSnap.applyPython("def go(n: float) -> None:\n    forward(n)\n\ngo(5)\n").fold(fail(_), identity)
    val reporter = PythonToSnap.replacing(command, "def go(n: float) -> float:\n    return n\n\nforward(go(5))\n").fold(fail(_), identity)
    val types = (state: ProgrammingStateRaw) => state.toSnapScalaModel.toOption.get.model.root.descendants.collect { case d: BlockDefinition => d.blockType }
    assertEquals((types(command), types(reporter)), (Vector(Some(BlockType.Command)), Vector(Some(BlockType.Reporter))))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(reporter.code), Nil)
  }
}
