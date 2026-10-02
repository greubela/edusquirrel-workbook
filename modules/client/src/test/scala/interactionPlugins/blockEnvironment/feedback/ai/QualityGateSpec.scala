package interactionPlugins.blockEnvironment.feedback.ai

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai.{PromptTemplates, QualityGate}
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis.{Diagnosis, DiagnosisCategory, TaskProfile}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{PythonRuntimeOutcome, PythonTestResult}
import munit.FunSuite

final class QualityGateSpec extends FunSuite {

  // Minimal constraints for testing: all checks enabled, 2 steps required
  private val baseConstraints = PromptTemplates.OutputConstraints(
    maxWords = 90,
    requireMentionedTestName = false,
    forbidCodeBlocks = true,
    forbidProvidingFullSolution = true,
    forbidMarkdown = true,
    forbidBackticks = true,
    forbidChitchat = true,
    minSteps = 2,
    maxSteps = 2,
    isGerman = true
  )

  private val simpleSource =
    """def max_in_list(xs):
      |    result = 0
      |    for x in xs:
      |        if x > result:
      |            result = x
      |            return result""".stripMargin

  // ── unsupported_structure_hint: false-positive regression tests ──

  test("'Versetze' should NOT trigger unsupported_structure_hint") {
    val text =
      """Deine Funktion gibt aktuell nur das Maximum des ersten Elements zurück.
        |1. Versetze die Rückgabe-Anweisung aus der Schleife.
        |2. Stelle sicher, dass der Anfangswert korrekt ist.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'Versetze' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'Setze' should NOT trigger unsupported_structure_hint") {
    val text =
      """Dein Code bricht zu früh ab.
        |1. Setze den Anfangswert von result korrekt.
        |2. Verschiebe die Rückgabe nach der Schleife.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'Setze' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'ersetze' should NOT trigger unsupported_structure_hint") {
    val text =
      """Dein Code gibt einen falschen Wert zurück.
        |1. Ersetze den Startwert durch einen passenden Wert.
        |2. Verschiebe return aus der Schleife heraus.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'ersetze' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'offset' should NOT trigger unsupported_structure_hint") {
    val text =
      """Your function returns an offset value instead of the maximum.
        |1. Move the return statement outside the loop.
        |2. Initialize result with a proper starting value.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'offset' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'reset' should NOT trigger unsupported_structure_hint") {
    val text =
      """Your loop seems to reset in each iteration.
        |1. Move the return statement after the loop ends.
        |2. Set the initial value of result appropriately.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'reset'/'Set the value' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'Set the value' (verb) should NOT trigger unsupported_structure_hint") {
    val text =
      """Your function returns the wrong value.
        |1. Set the starting value so it works with negative numbers.
        |2. Move return outside the loop.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"Verb 'Set' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  test("'predict' should NOT trigger unsupported_structure_hint") {
    val text =
      """Try to predict what your function returns for a small input.
        |1. Trace through each iteration step by step.
        |2. Move the return so it runs after the loop.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"'predict' falsely triggered unsupported_structure_hint. Reasons: ${result.reasons}"
    )
  }

  // ── unsupported_structure_hint: true-positive tests (must still fire) ──

  test("'a set' advice SHOULD trigger unsupported_structure_hint when no set in source") {
    val text =
      """Verwende ein Set, um doppelte Einträge zu entfernen.
        |1. Erstelle ein Set aus der Liste.
        |2. Gib das Maximum des Sets zurück.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      result.reasons.contains("unsupported_structure_hint"),
      s"Data-structure 'set' advice should trigger. Reasons: ${result.reasons}"
    )
  }

  test("'use a set' in English SHOULD trigger unsupported_structure_hint") {
    val text =
      """You should use a set to remove duplicates.
        |1. Convert the list to a set first.
        |2. Then find the maximum value.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      result.reasons.contains("unsupported_structure_hint"),
      s"English 'use a set' should trigger. Reasons: ${result.reasons}"
    )
  }

  test("standalone 'dict' advice SHOULD trigger unsupported_structure_hint when no dict in source") {
    val text =
      """Nutze ein dict, um die Werte zu speichern.
        |1. Erstelle ein dict für die Zuordnung.
        |2. Gib den maximalen Wert zurück.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      result.reasons.contains("unsupported_structure_hint"),
      s"Standalone 'dict' advice should trigger. Reasons: ${result.reasons}"
    )
  }

  test("'dictionary' SHOULD trigger unsupported_structure_hint") {
    val text =
      """Verwende ein Dictionary, um die Werte zu speichern.
        |1. Erstelle ein Dictionary für die Zuordnung.
        |2. Gib den maximalen Wert zurück.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, simpleSource)
    assert(
      result.reasons.contains("unsupported_structure_hint"),
      s"'dictionary' advice should trigger. Reasons: ${result.reasons}"
    )
  }

  test("'hashmap' SHOULD trigger unsupported_structure_hint") {
    val text =
      """Consider using a hashmap to store the values.
        |1. Create a hashmap mapping elements to counts.
        |2. Return the element with the highest count.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints.copy(isGerman = false), Seq.empty, simpleSource)
    assert(
      result.reasons.contains("unsupported_structure_hint"),
      s"'hashmap' advice should trigger. Reasons: ${result.reasons}"
    )
  }

  test("set advice is OK when source code actually uses set()") {
    val sourceWithSet =
      """def unique_max(xs):
        |    s = set(xs)
        |    return max(s)""".stripMargin
    val text =
      """Du verwendest ein Set korrekt, aber die Logik stimmt nicht.
        |1. Überprüfe, was set() mit deiner Liste macht.
        |2. Stelle sicher, dass du das Maximum korrekt zurückgibst.""".stripMargin
    val result = QualityGate.enforce(text, baseConstraints, Seq.empty, sourceWithSet)
    assert(
      !result.reasons.contains("unsupported_structure_hint"),
      s"Set advice should be allowed when source uses set(). Reasons: ${result.reasons}"
    )
  }

  test("one evidenced correction stays numbered without padding") {
    val text = "Your comparison selects the smaller value.\n1. Change the comparison direction."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1, maxSteps = 4), Nil)
    assert(result.passed, result.reasons.mkString(", "))
    assertEquals(result.finalText, text)
  }

  test("numbered and bulleted steps are renumbered consecutively") {
    val text = "Check the updates.\n4) Adjust the initial value.\n8. Move the return.\n* Keep the update inside the loop.\n• Use the updated result."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1, maxSteps = 4), Nil)
    assertEquals(result.finalText,
      "Check the updates.\n1. Adjust the initial value.\n2. Move the return.\n3. Keep the update inside the loop.\n4. Use the updated result.")
  }

  test("wrapped steps retain their continuation lines") {
    val text = "Your function returns too early.\n1. Move the return statement\n   after the loop finishes.\n2. Keep updating the result\n   inside the loop."
    val result = QualityGate.enforce(text, baseConstraints, Nil)
    assertEquals(result.finalText,
      "Your function returns too early.\n1. Move the return statement after the loop finishes.\n2. Keep updating the result inside the loop.")
  }

  test("decimal and negative observations are not steps") {
    val text = "3.14 is the current result.\n-5 is the required result.\n1) Correct the calculation."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1), Nil)
    assertEquals(result.finalText,
      "3.14 is the current result. -5 is the required result.\n1. Correct the calculation.")
  }

  test("missing numbered corrections request a rewrite without inventing steps") {
    val text = "Your comparison selects the smaller value."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1), Nil)
    assert(!result.passed)
    assert(result.reasons.contains("too_few_steps(0<1)"))
    assertEquals(result.finalText, text)
  }

  test("complete explanatory sentences are preserved") {
    val text = "Your function updates the result when it finds a smaller value.\nThat is why the largest value is lost.\n1. Change the comparison direction."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1, maxWords = 12), Nil)
    assertEquals(result.finalText,
      "Your function updates the result when it finds a smaller value. That is why the largest value is lost.\n1. Change the comparison direction.")
  }

  test("excess steps are dropped as complete items with their continuations") {
    val text = "Start with these corrections.\n1. Keep this correction.\n   Keep its explanation.\n2. Drop this correction.\n   Drop its explanation."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 1, maxSteps = 1), Nil)
    assertEquals(result.finalText,
      "Start with these corrections.\n1. Keep this correction. Keep its explanation.")
  }

  test("passing feedback does not acquire corrective steps") {
    val text = "Nicely done, your solution gives the expected results for these examples."
    val result = QualityGate.enforce(text, baseConstraints.copy(minSteps = 0), Nil)
    assert(result.passed, result.reasons.mkString(", "))
    assertEquals(result.finalText, text)
  }

  test("failure prompts require one numbered correction in both languages, success requires none") {
    for
      language <- Seq(AppLanguage.English, AppLanguage.German)
      passed <- Seq(false, true)
    do
      val tests = Seq("positive", "mixed", "negative").map(name =>
        PythonTestResult(name, passed, "9", if passed then "9" else "1", None))
      val signals = BlockFeedbackSignals(
        rawPython = simpleSource, pythonRules = Nil, vmRules = Nil,
        runtimeOutcome = PythonRuntimeOutcome.empty.copy(tests = tests),
        linesOfCode = 6, nonEmptyLineCount = 6, commentLineCount = 0, blankLineCount = 0,
        printCount = 0, stdoutLineCount = 0, stderrLineCount = 0)
      val decision = DecisionLayer.heuristicRoute(signals)
      val diagnosis = Diagnosis(DiagnosisCategory.Logic, decision.primaryIssue, Nil,
        decision.severity, decision.confidence, Nil, Nil, TaskProfile.Empty, Nil)
      val prompt = PromptTemplates.buildPrompt(signals, diagnosis, decision, language,
        tests.map(_.name), "Return the maximum value in the list.", simpleSource)
      assertEquals(prompt.constraints.minSteps, if passed then 0 else 1)
      assertEquals(prompt.constraints.maxSteps, if passed then 2 else 4)
      val evidenceRule = if language == AppLanguage.German then "Erfinde keine zusätzlichen Anforderungen" else "Do not invent extra requirements"
      assert(prompt.prompt.contains(evidenceRule))
      if !passed then assert(prompt.prompt.contains("(1., 2., ...)"))
  }
}
