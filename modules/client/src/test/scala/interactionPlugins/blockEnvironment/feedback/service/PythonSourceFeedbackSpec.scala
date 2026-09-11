package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai.LlmClient
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.config.BlockFeedbackConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.BlockFeedbackRequest
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.parsing.python.PythonParser
import munit.FunSuite

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final class PythonSourceFeedbackSpec extends FunSuite:
  private val noLlm = new LlmClient:
    def complete(prompt: String, systemPrompt: Option[String]): Future[String] =
      Future.failed(new AssertionError("Unexpected LLM request"))

  private val config = BlockFeedbackConfig(
    enablePythonStaticChecks = false,
    enableUnitTests = false,
    enableAiSummary = false
  )

  private val examples = Seq(
    "maximum" -> "def max_in_list(xs):\n    m = xs[0]\n    for x in xs:\n        if x < m:\n            m = x\n    return m\n",
    "slice" -> "def reverse(xs):\n    return xs[::-1]\n",
    "method call" -> "def upper(s):\n    return s.upper()\n"
  )

  examples.foreach { case (name, source) =>
    test(s"text Python does not run VM checks on its partial import: $name") {
      val request = BlockFeedbackRequest(
        exerciseText = LanguageMap.universalMap("Return the requested value."),
        studentCodePython = BeStartProgram(PythonParser.parsePython(source)),
        pythonSourceOverride = Some(source),
        submissionNr = 1,
        config = config
      )
      BlockFeedbackService.generateFeedback(request, noLlm).map { feedback =>
        assertEquals(feedback.debug.get.ruleHintsCount, 0)
        assert(!feedback.displayHints.mkString("\n").contains("quick checks"))
        assertEquals(feedback.rawPython, source)
      }
    }
  }

  test("native block submissions still run their configured VM checks") {
    val request = BlockFeedbackRequest(
      exerciseText = LanguageMap.universalMap("Write a program."),
      studentCodePython = BeStartProgram(),
      submissionNr = 1,
      config = config
    )
    BlockFeedbackService.generateFeedback(request, noLlm).map { feedback =>
      assert(feedback.debug.get.ruleHintsCount > 0)
    }
  }
