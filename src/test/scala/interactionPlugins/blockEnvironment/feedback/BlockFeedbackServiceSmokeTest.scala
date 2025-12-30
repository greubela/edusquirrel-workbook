package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.parsing.python.PythonParser
import interactionPlugins.pythonExercises.PythonRunStatus
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global

final class BlockFeedbackServiceSmokeTest extends FunSuite:

  private def exprFromPython(source: String): BeExpression =
    BeStartProgram(PythonParser.parsePython(source))

  private def dummyExerciseText: LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMap(
      Map[HumanLanguage, String](
        AppLanguage.English -> "Dummy exercise text"
      )
    )

  test("generateFeedback returns a Future with basic feedback") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("x = 1\nprint(x)"),
      submissionNr = 1,
      // keep unit tests disabled to avoid runtime dependency in tests
      config = BlockFeedbackConfig.default.copy(enableUnitTests = false)
    )

    BlockFeedbackService
      .generateFeedback(request)
      .map { feedback =>
        assert(feedback.rawPython.nonEmpty, "rawPython should not be empty")
        assertEquals(feedback.tests.isEmpty, true, "no runtime tests when disabled")
        assert(feedback.summary.nonEmpty, "summary should not be empty")
      }
  }

  test("feedback builder maps runtime outcomes") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("print('hi')"),
      submissionNr = 1,
      config = BlockFeedbackConfig.default.copy(enableUnitTests = true)
    )

    val plan = BlockFeedbackTestPlan(
      visibleTests = Nil,
      hiddenTests = Nil,
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 1000,
      derivedHints = Seq("hint-from-plan")
    )

    val outcome = PythonRuntimeOutcome(
      tests = Seq(
        PythonTestResult("t1", passed = true, expected = "ok", actual = "ok", message = None),
        PythonTestResult("t2", passed = false, expected = "ok", actual = "fail", message = Some("m2"))
      ),
      runStatus = Some(PythonRunStatus.Success),
      normalizedScore = Some(0.5),
      runtimeError = None
    )

    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(request, plan, outcome)

    assertEquals(feedback.tests.size, 2)
    assert(feedback.generalHints.exists(_.contains("hint-from-plan")))
    assert(feedback.generalHints.exists(_.contains("m2")))
    assert(feedback.summary.contains("Passed"))
  }
