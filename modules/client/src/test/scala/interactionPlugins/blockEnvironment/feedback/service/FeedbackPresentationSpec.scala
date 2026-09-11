package interactionPlugins.blockEnvironment.feedback.service

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.config.BlockFeedbackConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{BlockFeedbackRequest, PythonRuntimeOutcome, PythonTestResult}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules.{RuleResult, RuleSeverity}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.runtime.PythonRunStatus
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service.{BlockFeedbackFeedbackBuilder, BlockFeedbackTestPlan}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.parsing.python.PythonParser
import munit.FunSuite

final class FeedbackPresentationSpec extends FunSuite:
  private val maximumSource =
    """def maximum(xs):
      |    m = xs[0]
      |    for x in xs:
      |        if x > m:
      |            m = x
      |    return m
      |""".stripMargin

  private def request(language: HumanLanguage = AppLanguage.English): BlockFeedbackRequest =
    BlockFeedbackRequest(
      exerciseText = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
        AppLanguage.English -> "Return the maximum value in the list.",
        AppLanguage.German -> "Gib den größten Wert der Liste zurück."
      )),
      studentCodePython = BeStartProgram(PythonParser.parsePython(maximumSource)),
      pythonSourceOverride = Some(maximumSource),
      submissionNr = 1,
      config = BlockFeedbackConfig(enableUnitTests = true),
      humanLanguage = language
    )

  private val plan = BlockFeedbackTestPlan(Nil, Nil, Nil, Nil, 5000, Nil)
  private val passedTest = PythonTestResult("maximum", true, "3", "3", None)
  private val success = PythonRuntimeOutcome(Seq(passedTest), Some(PythonRunStatus.Success), Some(1.0), None, None, None)
  private val vmWarnings = Seq(
    RuleResult("VM_UNUSED_VARIABLES", "unused", RuleSeverity.Warning, false, "The following variables are never used: xs."),
    RuleResult("VM_SYNTAX", "syntax", RuleSeverity.Error, false, "Unknown Python structure: xs[0]"),
    RuleResult("VM_TYPE", "type", RuleSeverity.Error, false, "value BeExpressionUnsupported(xs[0]) for assigning must be able to evaluate to BeDataTypeAtomic(MapBasedLanguageMap(...))!"),
    RuleResult("VM_CAST", "type", RuleSeverity.Warning, false, "Implicit Cast: it.evadid.vm.types.BeDataType$AnyType$@8de")
  )

  test("text Python does not display warnings from its incomplete block model") {
    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(request(), plan, success, Nil, vmWarnings)
    val hints = feedback.displayHints.mkString("\n")
    vmWarnings.foreach(warning => assert(!hints.contains(warning.message), hints))
    assert(!hints.contains("quick checks"), hints)
    assert(feedback.allTestsPassed)
  }

  test("text Python keeps its real source warnings") {
    val warning = RuleResult("PY_INPUT", "io", RuleSeverity.Warning, false, "Do not call input() inside this function.")
    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(request(), plan, success, Seq(warning), vmWarnings)
    assert(feedback.displayHints.exists(_.contains(warning.message)))
    assert(!feedback.displayHints.mkString("\n").contains("BeExpressionUnsupported"))
  }

  test("native block programs keep their block-model warnings") {
    val nativeRequest = request().copy(
      studentCodePython = BeStartProgram(PythonParser.parsePython("print(1)")),
      pythonSourceOverride = None
    )
    val warning = vmWarnings.head
    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(nativeRequest, plan, success, Nil, Seq(warning))
    assert(feedback.displayHints.exists(_.contains(warning.message)))
  }

  test("failed maximum keeps the tutor explanation and numbered next steps") {
    val advice = "Your function currently returns the smallest value.\n\n1. Change the comparison from < to >.\n2. Try your code again with the supplied examples."
    val failed = success.copy(tests = Seq(passedTest.copy(passed = false, actual = "1")), normalizedScore = Some(0.0))
    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(
      request().copy(pythonSourceOverride = Some(maximumSource.replace("x > m", "x < m"))),
      plan.copy(derivedHints = Seq(advice)), failed, Nil, vmWarnings
    )
    assertEquals(feedback.displayHints, Seq(advice))
    assert(!feedback.allTestsPassed)
  }

  test("bullet suggestions remain separate numbered steps") {
    val advice = "Check your comparison.\n\n- Change < to >.\n- Run the examples again."
    val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(request(), plan.copy(derivedHints = Seq(advice)), success, Nil, Nil)
    assertEquals(feedback.displayHints, Seq("Check your comparison.\n\n1. Change < to >.\n2. Run the examples again."))
  }
