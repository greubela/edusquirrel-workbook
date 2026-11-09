package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleGraphic, TurtleDrawingPolicy, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

class RecreateShapeGraderSpec extends FunSuite {
  test("the reported Python staircase grades correctly in every supported source representation") {
    val state = ProgrammingStatePythonString("forward(100)\nturn_right(90)\nforward(100)\nturn_right(270)\nforward(100)")
    val target = TurtleGraphic.TurtleGraphicSvgString("M 0 0 L 100 0 L 100 100 L 200 100")
    val commands = state.toBeExpression.deriveTurtleCommands
    assertEquals(commands.size, 5, clues(commands))
    Future.traverse(List(state, state.toSnapXml, state.toJava)) { source =>
      RecreateShapeGrader(target, GradingImportance.REQUIRED_TEST).gradeStateSafe(source, true, TestExecutionContext()).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Correct(), clues(source, commands))
      }
    }

  }

  test("interaction grading follows its drawing policy for travel and subdivision") {
    val cases = List(
      ("forward(5)\nforward(5)", "M0 0 L10 0", List(GradingStatus.Incorrect(), GradingStatus.Incorrect(), GradingStatus.Correct())),
      ("forward(10)\npen_up()\ngoto_x_y(10, 5)\ngoto_x_y(20, 0)\npen_down()\nforward(10)", "M0 0 L10 0 M20 0 L30 0",
        List(GradingStatus.PartiallyCorrect(), GradingStatus.Correct(), GradingStatus.Correct())))
    Future.traverse(cases) { (program, path, grades) =>
      Future.traverse(TurtleDrawingPolicy.values.toList.zip(grades)) { (policy, expected) =>
        val interaction = TurtleRecreateShapeInteraction(s"policy-$policy", ProgrammingStatePythonString(program),
          TurtleGraphic.TurtleGraphicSvgString(path), limitTurtleCommandUsage = Map.empty, comparisonPolicy = policy)
        interaction.interactionVariable.control.getOrCalculateGrading(TestExecutionContext()).map { result =>
          assertEquals(result.testsWithResults.head.gradingStatus, expected, clues(policy, program))
        }
      }
    }
  }

  test("a completed earlier draft cannot grade the corrected staircase") {
    val target = TurtleGraphic.TurtleGraphicSvgString("M0 0 L100 0 L100 100 L200 100")
    val first = ProgrammingStatePythonString("forward(100)\nforward(100)")
    val corrected = ProgrammingStatePythonString("forward(100)\nturn_right(90)\nforward(100)\nturn_right(270)\nforward(100)")
    val interaction = TurtleRecreateShapeInteraction("edited-staircase", first, target, limitTurtleCommandUsage = Map.empty)
    val variable = interaction.interactionVariable
    variable.control.getOrCalculateGrading(TestExecutionContext()).flatMap { old =>
      assertEquals(old.testsWithResults.head.gradingStatus, GradingStatus.PartiallyCorrect())
      variable.setStateFromUserInteraction(corrected, MAJOR, context = TestExecutionContext())
      variable.control.getOrCalculateGrading(TestExecutionContext()).map { current =>
        assertEquals(current.state, corrected)
        assertEquals(current.testsWithResults.head.gradingStatus, GradingStatus.Correct())
        assertEquals(variable.currentHistory.historyGrading.gradingsMap(first), old)
      }
    }
  }
}
