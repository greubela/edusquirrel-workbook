package it.evadid.workbook.interactions.programming

import it.evadid.workbook.support.OfflineController

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleGraphic, TurtleDrawingPolicy, TurtleRecreateShapeInteraction}
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.sync.UpdateImportance.MAJOR
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

class RecreateShapeGraderSpec extends FunSuite {
  test("the reported Python staircase grades correctly in every supported source representation") {
    val state = ProgrammingStateRaw(Python, "forward(100)\nturn_right(90)\nforward(100)\nturn_right(270)\nforward(100)")
    val target = TurtleGraphic.TurtleGraphicSvgString("M 0 0 L 100 0 L 100 100 L 200 100")
    val commands = state.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(commands.size, 5, clues(commands))
    Future.traverse(List(state, state.toSnapXml, state.toJava)) { source =>
      RecreateShapeGrader(target, GradingImportance.REQUIRED_TEST).gradeStateSafe(source, true, MAJOR, TestExecutionContext()).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Correct, clues(source, commands))
      }
    }

  }

  test("interaction grading follows its drawing policy for travel and subdivision") {
    val cases = List(
      ("forward(5)\nforward(5)", "M0 0 L10 0", List(GradingStatus.Incorrect, GradingStatus.Incorrect, GradingStatus.Correct)),
      ("forward(10)\npen_up()\ngoto_x_y(10, 5)\ngoto_x_y(20, 0)\npen_down()\nforward(10)", "M0 0 L10 0 M20 0 L30 0",
        List(GradingStatus.PartiallyCorrect, GradingStatus.Correct, GradingStatus.Correct)))
    Future.traverse(cases) { (program, path, grades) =>
      Future.traverse(TurtleDrawingPolicy.values.toList.zip(grades)) { (policy, expected) =>
        val interaction = TurtleRecreateShapeInteraction(s"policy-$policy", ProgrammingStateRaw(Python, program),
          TurtleGraphic.TurtleGraphicSvgString(path), comparisonPolicy = policy)
        // The unedited default answer has no event, so its grading must be requested.
        OfflineController.of(interaction).feedback(interaction).getOrCalculateGrading(forceGrading = true).map { result =>
          assertEquals(result.testsWithResults.head.gradingStatus, expected, clues(policy, program))
        }
      }
    }
  }

  test("a completed earlier draft cannot grade the corrected staircase") {
    val target = TurtleGraphic.TurtleGraphicSvgString("M0 0 L100 0 L100 100 L200 100")
    val first = ProgrammingStateRaw(Python, "forward(100)\nforward(100)")
    val corrected = ProgrammingStateRaw(Python, "forward(100)\nturn_right(90)\nforward(100)\nturn_right(270)\nforward(100)")
    val interaction = TurtleRecreateShapeInteraction("edited-staircase", first, target)
    val answers = OfflineController.of(interaction)
    // Each edit's automatic grading skips the shape grader; the requested one after it runs it.
    def requested() = answers.feedback(interaction).getOrCalculateGrading()
      .flatMap(_ => answers.feedback(interaction).getOrCalculateGrading(forceGrading = true))
    answers.setStateFromUserInteraction(interaction, first, MAJOR)
    requested().flatMap { old =>
      assertEquals(old.testsWithResults.head.gradingStatus, GradingStatus.PartiallyCorrect)
      answers.setStateFromUserInteraction(interaction, corrected, MAJOR)
      requested().map { current =>
        assertEquals(current.state, corrected)
        assertEquals(current.testsWithResults.head.gradingStatus, GradingStatus.Correct)
        assertEquals(answers.currentHistory(interaction).gradingForState(interaction.gradingElements, first), old)
      }
    }
  }

  test("edits do not run the shape comparison; only a requested grading does and records it") {
    val target = TurtleGraphic.TurtleGraphicSvgString("M0 0 L10 0")
    val interaction = TurtleRecreateShapeInteraction("manual-shape", ProgrammingStateRaw(Python, "forward(5)"), target)
    val answers = OfflineController.of(interaction)
    answers.setStateFromUserInteraction(interaction, ProgrammingStateRaw(Python, "forward(10)"), MAJOR)
    answers.feedback(interaction).getOrCalculateGrading().flatMap { automatic =>
      assertEquals(automatic.testsWithResults.head.gradingStatus, GradingStatus.NotRequested)
      assert(answers.currentHistory(interaction).gradingEvents.isEmpty, "an edit records no grading events")
      answers.feedback(interaction).getOrCalculateGrading(forceGrading = true).map { requested =>
        assertEquals(requested.testsWithResults.head.gradingStatus, GradingStatus.Correct)
      }
    }
  }
}
