package it.evadid.workbook.model.feedback

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.vm.parsing.python.PythonParser
import it.evadid.workbook.model.feedback.runtime.*
import munit.FunSuite
import upickle.default.*

class SharedFeedbackModelsSpec extends FunSuite {
  private def legacy[T: ReadWriter](name: String): T = {
    val json = ujson.read(FeedbackLegacyWireFixtures.json(name))
    val decoded = read[T](json)
    assertEquals(writeJs(decoded), json, s"Legacy $name fields and enum names remain unchanged")
    assertEquals(writeJs(readBinary[T](writeBinary(decoded))), json)
    decoded
  }

  test("all feedback and runtime enums retain their legacy names") {
    assertEquals(legacy[List[RouterMode]]("router"), RouterMode.values.toList)
    assertEquals(legacy[List[PythonRunStatus]]("runStatus"), PythonRunStatus.values.toList)
    assertEquals(legacy[List[PythonTestStatus]]("testStatus"), PythonTestStatus.values.toList)
    assertEquals(legacy[List[FeedbackStatus]]("feedbackStatus"), FeedbackStatus.values.toList)
  }

  test("legacy request, test configuration and fixture payloads remain usable") {
    val config = legacy[BlockFeedbackConfig]("config")
    assertEquals(config.routerMode, RouterMode.Ml)
    assertEquals(config.visibleTests.head.hintDE, Some("Hinweis"))
    assertEquals(config.fixtures.head.content, "学校")
    val request = legacy[BlockFeedbackRequest]("request")
    assertEquals(request.config, config)
    assertEquals(request.meta, BlockFeedbackMeta(Some("exercise"), Some("user")))
    assertEquals(request.pythonSource, "print(1)\n")
    assertEquals(request.preferredHumanLanguage, AppLanguage.English)
  }

  test("request source fallback derives executable text on both platforms") {
    val request = BlockFeedbackRequest(LanguageMap.universalMap("Exercise"),
      PythonParser.parsePython("print(42)"), submissionNr = 1, config = BlockFeedbackConfig())
    assert(request.pythonSource.contains("print"))
    assert(request.pythonSource.contains("42"))
    val restored = read[BlockFeedbackRequest](write(request))
    assertEquals(restored.pythonSource, request.pythonSource)
    assertEquals(restored.pythonSourceOverride, None)
  }

  test("legacy outcomes and feedback preserve prepared display data and timestamps") {
    val outcome = legacy[PythonRuntimeOutcome]("outcome")
    assertEquals(outcome.runStatus, Some(PythonRunStatus.Failed))
    assertEquals(outcome.normalizedScore, Some(0.5))
    val feedback = legacy[UltrichsNewCoolFeedback]("feedback")
    assertEquals(feedback.timestampEpochMillis, 123456789L)
    assertEquals(feedback.displayTests.head.expectedActual,
      Some(ExpectedActual("Expected", "1", "Actual", "2")))
    assertEquals(feedback.status, FeedbackStatus.FINISHED)
    val contract: FeedbackResult = feedback
    assertEquals(contract.timestampEpochMillis, feedback.timestampEpochMillis)
  }

  test("runtime requests and results need no browser to decode") {
    val request = legacy[PythonRunRequest]("runtimeRequest")
    assertEquals(request.fixtures.head.content, "学校")
    assertEquals(request.timeoutMs, 5000)
    val result = legacy[PythonRunResult]("runtimeResult")
    assertEquals(result.tests.head.status, PythonTestStatus.Failed)
    assertEquals(result.tests.head.durationMs, 2.5)
    assertEquals(result.score, 0.5)
  }

  test("configuration constructor defaults remain distinct from pipeline defaults") {
    assertEquals(read[BlockFeedbackConfig]("{}"), BlockFeedbackConfig())
    assertEquals(BlockFeedbackConfig().enableAiSummary, false)
    assertEquals(BlockFeedbackConfig.default.enableAiSummary, true)
    assertEquals(read[BlockFeedbackConfig](write(BlockFeedbackConfig.default)), BlockFeedbackConfig.default)
    assertEquals(PythonRuntimeOutcome.empty, PythonRuntimeOutcome(Nil, None, None, None, None, None))
  }

  test("feedback debug metadata is serializable independently of the UI") {
    val debug = FeedbackDebug(true, true, false, 1, 2, 3, 4, 2, true, false,
      "runtime", Some("template"), llmRewriteCount = 2, llmLastGateReasons = Seq("reason"),
      functionNameMismatch = Seq("expectedName"), rawRuntimeError = Some("ValueError"))
    assertEquals(read[FeedbackDebug](write(debug)), debug)
    assertEquals(readBinary[FeedbackDebug](writeBinary(debug)), debug)
  }
}
