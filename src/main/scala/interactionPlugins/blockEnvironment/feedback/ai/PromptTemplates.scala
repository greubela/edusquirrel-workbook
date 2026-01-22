package interactionPlugins.blockEnvironment.feedback.ai

import contentmanagement.model.language.{AppLanguage, HumanLanguage}
import interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer}

/**
 * Prompt building + strict constraints for post-routing LLM usage.
 *
 * This module is pure (no network calls). A future LLM client can plug in by
 * taking `buildPrompt(...).prompt` and returning text.
 */
object PromptTemplates {

  private trait IssueModule {
    def focusLines(humanLanguage: HumanLanguage, signals: BlockFeedbackSignals): Seq[String]
  }

  private object IssueModule {
    private val Default: IssueModule = (humanLanguage, _) =>
      if humanLanguage == AppLanguage.German then
        Seq("Fokus: konkrete Ursache eingrenzen und 2–4 passende Schritte nennen.")
      else
        Seq("Focus: narrow down the concrete cause and give 2–4 matching steps.")

    private val FormatOutput: IssueModule = (humanLanguage, signals) =>
      if humanLanguage == AppLanguage.German then
        Seq(
          "Fokus: Ausgabe/Format (Whitespace, Zeilenumbrüche, zusätzliche Prints).",
          s"Hinweis: printCount=${signals.printCount}, stdoutLines=${signals.stdoutLineCount}."
        )
      else
        Seq(
          "Focus: output/format (whitespace, newlines, extra prints).",
          s"Hint: printCount=${signals.printCount}, stdoutLines=${signals.stdoutLineCount}."
        )

    private val IoContract: IssueModule = (humanLanguage, signals) =>
      if humanLanguage == AppLanguage.German then
        Seq(
          "Fokus: I/O-Vertrag (input() vermeiden, return statt print wenn gefordert).",
          s"Hinweis: inputCallCount=${signals.inputCallCount}, printCount=${signals.printCount}."
        )
      else
        Seq(
          "Focus: I/O contract (avoid input(), return vs print as required).",
          s"Hint: inputCallCount=${signals.inputCallCount}, printCount=${signals.printCount}."
        )

    private val Boundary: IssueModule = (humanLanguage, signals) =>
      if humanLanguage == AppLanguage.German then
        Seq(s"Fokus: Grenzfall/Edge-Case (boundaryHintScore=${signals.boundaryHintScore}).")
      else
        Seq(s"Focus: boundary/edge case (boundaryHintScore=${signals.boundaryHintScore}).")

    private val ExceptionType: IssueModule = (humanLanguage, _) =>
      if humanLanguage == AppLanguage.German then Seq("Fokus: Exception-Typ verstehen und den fehlerhaften Zugriff robust machen.")
      else Seq("Focus: understand the exception type and make the failing access robust.")

    private val Incomplete: IssueModule = (humanLanguage, signals) =>
      if humanLanguage == AppLanguage.German then
        Seq(s"Fokus: unvollständige Implementierung (pass=${signals.hasPassStatement}).")
      else
        Seq(s"Focus: incomplete implementation (pass=${signals.hasPassStatement}).")

    private val Performance: IssueModule = (humanLanguage, _) =>
      if humanLanguage == AppLanguage.German then Seq("Fokus: unnötige Wiederholungen/verschachtelte Schleifen reduzieren.")
      else Seq("Focus: reduce unnecessary repeats / nested loops.")

    def forIssue(issueType: DecisionLayer.IssueType): IssueModule = issueType match {
      case DecisionLayer.IssueType.FORMAT_OUTPUT => FormatOutput
      case DecisionLayer.IssueType.IO_CONTRACT => IoContract
      case DecisionLayer.IssueType.BOUNDARY_CONDITION => Boundary
      case DecisionLayer.IssueType.EXCEPTION_TYPE => ExceptionType
      case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION => Incomplete
      case DecisionLayer.IssueType.PERFORMANCE => Performance
      case _ => Default
    }
  }

  final case class OutputConstraints(
    maxWords: Int,
    requireMentionedTestName: Boolean,
    forbidCodeBlocks: Boolean,
    forbidProvidingFullSolution: Boolean,
    forbidMarkdown: Boolean,
    forbidBackticks: Boolean,
    forbidChitchat: Boolean,
    minSteps: Int,
    maxSteps: Int,
    issueTypeHint: Option[DecisionLayer.IssueType] = None
  )

  final case class Prompt(
    prompt: String,
    constraints: OutputConstraints,
    testNames: Seq[String]
  )

  private def isGerman(lang: HumanLanguage): Boolean = lang == AppLanguage.German

  private def extractFunctionNameHint(signals: BlockFeedbackSignals, visibleFailingTestNames: Seq[String]): Option[String] = {
    val visibleFailing =
      signals.runtimeOutcome.tests
        .filterNot(_.passed)
        .filter(t => visibleFailingTestNames.contains(t.name))

    val re = "(?i)assert\\s+([a-zA-Z_]\\w*)\\s*\\(".r
    visibleFailing
      .flatMap(_.expected.split("\n").toSeq)
      .flatMap(line => re.findFirstMatchIn(line).map(_.group(1)))
      .headOption
  }

  private def extractCodeObservations(rawPython: String): Seq[String] = {
    val code = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    val lines = code.split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)

    def firstMatch(re: scala.util.matching.Regex): Option[String] =
      lines.collectFirst { case l if re.findFirstIn(l).nonEmpty => l }

    val obs = scala.collection.mutable.ArrayBuffer.empty[String]

    // Common operator-level mismatches (kept generic, no exercise assumptions).
    firstMatch("(?i)^return\\s+.*\\s-\\s.*$".r).foreach(_ => obs += "I see a return expression using '-' (subtraction).")
    firstMatch("(?i)^return\\s+.*\\s\\+\\s.*$".r).foreach(_ => obs += "I see a return expression using '+' (addition).")
    firstMatch("(?i)\\bif\\s+\\w+\\s<\\s\\w+\\s*:$".r).foreach(_ => obs += "I see a '<' comparison in an update condition inside a block.")
    firstMatch("(?i)\\bif\\s+\\w+\\s>\\s\\w+\\s*:$".r).foreach(_ => obs += "I see a '>' comparison in an update condition inside a block.")

    obs.toSeq.distinct.take(4)
  }

  /**
   * Builds a strict prompt for the LLM.
   *
   * The LLM must be guided to: be short, refer to concrete failing tests,
   * and provide actionable steps without giving a full solution.
   */
  def buildPrompt(
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision,
    humanLanguage: HumanLanguage,
    visibleTestNames: Seq[String],
    exerciseText: String,
    rawPython: String
  ): Prompt = {
    val failedTests =
      signals.runtimeOutcome.tests
        .filterNot(_.passed)
        .filter(t => visibleTestNames.contains(t.name))

    val testNames = failedTests.map(_.name).distinct.take(5)
    val primaryTestName = testNames.headOption

    val functionNameHint = extractFunctionNameHint(signals, testNames)
    val maxWords = 70

    val constraints = OutputConstraints(
      maxWords = maxWords,
      requireMentionedTestName = false,
      forbidCodeBlocks = true,
      forbidMarkdown = true,
      forbidBackticks = true,
      forbidChitchat = true,
      forbidProvidingFullSolution = true,
      minSteps = if decision.primaryIssue == DecisionLayer.IssueType.COMPILE_ERROR then 2 else 2,
      maxSteps =
        decision.primaryIssue match {
          case DecisionLayer.IssueType.COMPILE_ERROR => 3
          case DecisionLayer.IssueType.PERFORMANCE   => 4
          case _                                     => 4
        },
      issueTypeHint = Some(decision.primaryIssue)
    )

    val testsSection =
      if testNames.isEmpty then "<no failing tests available>"
      else
        failedTests
          .take(5)
          .map { t =>
            val exp = t.expected.replace("\r\n", "\n")
            val expShort = if exp.length <= 120 then exp else exp.take(120) + "…"
            val act = t.actual.replace("\r\n", "\n")
            val actShort = if act.length <= 80 then act else act.take(80) + "…"
            s"- ${t.name}: expected=${expShort} actual=${actShort}"
          }
          .mkString("\n")

    val behaviorExamples =
      if failedTests.isEmpty then "<no failing cases available>"
      else
        failedTests
          .take(2)
          .map { t =>
            val exp = t.expected.replace("\r\n", "\n")
            val expShort = if exp.length <= 120 then exp else exp.take(120) + "…"
            val act = t.actual.replace("\r\n", "\n")
            val actShort = if act.length <= 80 then act else act.take(80) + "…"
            s"- expected=${expShort} observed=${actShort}"
          }
          .mkString("\n")

    val codeObservations =
      val obs = extractCodeObservations(rawPython)
      if obs.isEmpty then "<none>" else obs.map(o => s"- $o").mkString("\n")

    val contextHints = {
      val stdoutInfo = if signals.stdoutLineCount > 0 then s"stdout lines: ${signals.stdoutLineCount}" else "stdout empty"
      val printInfo = s"printCount: ${signals.printCount}"
      val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
      val runtimeErrShort = if runtimeErr.length <= 200 then runtimeErr else runtimeErr.take(200) + "…"
      val exText = Option(exerciseText).getOrElse("").replace("\r\n", "\n").trim
      val exTextShort = if exText.length <= 700 then exText else exText.take(700) + "…"
      val code = Option(rawPython).getOrElse("").replace("\r\n", "\n").trim
      val codeShort = if code.length <= 700 then code else code.take(700) + "…"
      Seq(stdoutInfo, printInfo, s"runtimeError: ${if runtimeErrShort.isEmpty then "<none>" else runtimeErrShort}")
        .mkString("\n") +
        "\n\nExercise statement (for requirements):\n" + (if exTextShort.isEmpty then "<empty>" else exTextShort) +
        "\n\nStudent code excerpt (do NOT quote or rewrite code; use it only to reason about behavior):\n" +
        (if codeShort.isEmpty then "<empty>" else codeShort)
    }

    val instruction =
      val focus = IssueModule.forIssue(decision.primaryIssue).focusLines(humanLanguage, signals).mkString("\n")
      if isGerman(humanLanguage) then
        s"""Du bist ein Tutor. Gib eine kurze, konkrete Hilfestellung.
           |
           |Ton & Stil:
           |- Schreibe wie eine echte Chat-Antwort (direkt, ruhig, hilfreich).
          |- Sprich die Person direkt an ("du").
           |- Kein Gruß, kein Smalltalk, keine Floskeln am Ende.
           |- Kein Markdown/Formatierung, keine Backticks.
           |- Keine Überschriften wie „Feedback:“ oder „Schritte:“; einfach normaler Text.
           |
           |Zwingende Regeln:
           |- Maximal ${constraints.maxWords} Wörter.
          |- Nenne keine Testnamen. Beschreibe stattdessen in Alltagssprache den fehlschlagenden Fall.
          |- Verwende keine Formulierungen wie „Der Test erwartet …“. Sag direkt, was erwartet ist und was dein Code aktuell macht.
           |- Gib 2–4 konkrete Schritte (als nummerierte Liste).
           |- Keine Lösung ausformulieren, kein vollständiger Code, keine Codeblöcke.
          |- Schritte müssen zum konkreten Verhalten passen (keine generischen Tipps).
           |- Du musst NICHT immer expected/actual zitieren. Wenn es hilft, formuliere es natürlich: „Der Test erwartet X, aber dein Code liefert Y.“
          |- Du darfst genau ein sehr kurzes Code-Fragment zitieren (max. 1 Zeile), um die Ursache zu benennen. Keine korrigierte Version zeigen.
           |
           |Hinweis zur Funktion:
           |- functionName: ${functionNameHint.getOrElse("<unknown>")}
           |
           |Issue-Fokus:
           |$focus
           |
           |Routing:
           |- primaryIssue: ${decision.primaryIssue}
           |- secondaryIssues: ${decision.secondaryIssues.mkString(", ")}
           |- severity: ${decision.severity}
           |- causes: ${decision.topCauses.mkString("; ")}
           |- evidence: ${decision.evidence.map(e => s"${e.key}=${e.value}").mkString("; ")}
           |
            |Fehlschlagende Fälle (nur Kontext, nicht zitieren):
            |$behaviorExamples
            |
            |Code-Beobachtungen (optional, nicht alles erwähnen):
            |$codeObservations
           |
           |Zusätzliche Signale:
           |$contextHints
           |""".stripMargin
      else
          s"""You are a tutor. Provide a short, concrete hint.
           |
           |Tone & style:
           |- Write like a real chat reply (direct, calm, helpful).
            |- Address the student directly ("you").
           |- No greeting, no small talk, no motivational closing.
           |- No Markdown/formatting, no backticks.
           |- No headings like “Feedback:” or “Steps:”; just normal text.
           |
           |Hard rules:
           |- At most ${constraints.maxWords} words.
            |- Do not mention test names. Describe the failing case in plain language.
            |- Do not write “the test expects …”. State directly what is expected vs what your code currently does.
           |- Provide 2–4 concrete steps (numbered list).
           |- Do not provide the full solution, no full code, no code blocks.
            |- Steps must match the observed behavior (avoid generic advice).
           |- You do NOT have to quote expected/actual. If helpful, phrase it naturally: “The test expects X, but your code produces Y.”
            |- You may quote exactly one short code fragment (max 1 line) to point to the cause. Do not show a corrected version.
           |
           |Function hint:
           |- functionName: ${functionNameHint.getOrElse("<unknown>")}
           |
           |Issue focus:
           |$focus
           |
           |Routing:
           |- primaryIssue: ${decision.primaryIssue}
           |- secondaryIssues: ${decision.secondaryIssues.mkString(", ")}
           |- severity: ${decision.severity}
           |- causes: ${decision.topCauses.mkString("; ")}
           |- evidence: ${decision.evidence.map(e => s"${e.key}=${e.value}").mkString("; ")}
           |
           |Failing cases (context only, do not quote):
           |$behaviorExamples
           |
           |Code observations (optional, don't mention all):
           |$codeObservations
           |
           |Extra signals:
           |$contextHints
           |""".stripMargin

    Prompt(instruction, constraints, primaryTestName.toSeq)
  }

  /**
   * Deterministic fallback used until a real LLM is plugged in.
   * It is intentionally short, test-named, and avoids giving a full solution.
   */
  def deterministicDraft(
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision,
    humanLanguage: HumanLanguage
  ): String = {
    val failing = signals.runtimeOutcome.tests.filterNot(_.passed)
    val firstTestName = failing.headOption.map(_.name).getOrElse("<test>")
    val functionNameHint = extractFunctionNameHint(signals, failing.map(_.name).distinct.take(5))

    if isGerman(humanLanguage) then
      decision.primaryIssue match {
        case DecisionLayer.IssueType.FORMAT_OUTPUT =>
          s"""Okay, das wirkt wie ein Ausgabe/Format-Thema bei "$firstTestName".
             |1. Schau dir im Test expected vs actual an (Whitespace/Zeilenumbrüche sind oft der Grund).
             |2. Entferne zusätzliche Prints oder mache die Ausgabe exakt passend (printCount=${signals.printCount}).
             |3. Prüfe, dass du wirklich nur das ausgibst, was der Test erwartet.
             |""".stripMargin
        case DecisionLayer.IssueType.IO_CONTRACT =>
          s"""Der Test "$firstTestName" sieht nach einem I/O-Vertragsthema aus.
             |1. Prüfe, ob du irgendwo input() verwendest (inputCallCount=${signals.inputCallCount}) — Tests erwarten oft eine pure Funktion ohne Eingabe.
             |2. Wenn du etwas ausgibst: stelle sicher, dass die Funktion stattdessen den Wert zurückgibt (oder dass die Ausgabe exakt passt).
             |3. Vergleiche expected vs actual und entferne alles, was nicht gefordert ist.
             |""".stripMargin
        case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION =>
          s"""Bei "$firstTestName" wirkt es so, als ob die Implementierung noch nicht vollständig ist.
             |1. Suche nach pass/TODO-Platzhaltern und ersetze sie durch echte Logik.
             |2. Stelle sicher, dass deine Funktion für alle Fälle wirklich ein Ergebnis zurückgibt.
             |3. Teste mit einem kleinen Beispiel und vergleiche dann mit expected.
             |""".stripMargin
        case DecisionLayer.IssueType.BOUNDARY_CONDITION =>
          s"""Der primäre Test "$firstTestName" deutet auf einen Grenzfall hin.
             |1. Prüfe leere/kleine Eingaben, negative Werte oder genau-1-Element-Fälle.
             |2. Kontrolliere Initialisierung (erstes Element vs fester Startwert) und Vergleichsoperatoren.
             |3. Vergleiche expected/actual und passe die Logik für genau diesen Grenzfall an.
             |""".stripMargin
        case DecisionLayer.IssueType.EXCEPTION_TYPE =>
          s"""Bei "$firstTestName" scheint dein Code mit einer Exception abzubrechen.
             |1. Lies die Fehlermeldung (Type/Index/Key/etc.) und finde die Stelle, wo der falsche Typ/Index entsteht.
             |2. Prüfe Annahmen über Eingaben (leere Liste, None, falscher Datentyp).
             |3. Mach den fehlerhaften Zugriff/Umwandlung robust und teste erneut.
             |""".stripMargin
        case DecisionLayer.IssueType.PERFORMANCE =>
          s"""Der Test "$firstTestName" deutet auf ein Performance-Problem hin.
             |1. Suche nach unnötigen verschachtelten Schleifen oder wiederholten Scans über dieselben Daten.
             |2. Überlege, ob du mit einem einzigen Durchlauf auskommst.
             |3. Teste dann erneut und prüfe, ob der Timeout verschwindet.
             |""".stripMargin
        case _ =>
          s"""Okay, lass uns "$firstTestName" eingrenzen${functionNameHint.map(fn => s" (Fokus: $fn)").getOrElse("")}.
             |1. Prüfe, wie du das Ergebnis initialisierst (z.B. erstes Element vs. fester Startwert).
             |2. Geh alle Elemente durch und aktualisiere das Ergebnis nur, wenn du einen besseren Kandidaten findest.
             |3. Teste kurz Grenzfälle und vergleiche mit expected.
             |""".stripMargin
      }
    else
      decision.primaryIssue match {
        case DecisionLayer.IssueType.FORMAT_OUTPUT =>
          s"""Ok, this looks like an output/format mismatch in "$firstTestName".
             |1. Check expected vs actual (whitespace/newlines are common culprits).
             |2. Remove extra prints or make output match exactly (printCount=${signals.printCount}).
             |3. Make sure you don't print anything beyond what the test expects.
             |""".stripMargin
        case DecisionLayer.IssueType.IO_CONTRACT =>
          s"""The test "$firstTestName" looks like an I/O contract issue.
             |1. Check whether you call input() (inputCallCount=${signals.inputCallCount}) — many tests expect a pure function without reading input.
             |2. If you print anything, confirm the task expects printing; otherwise return the value.
             |3. Compare expected vs actual and remove anything not required.
             |""".stripMargin
        case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION =>
          s"""For "$firstTestName" it looks like the implementation is incomplete.
             |1. Look for pass/TODO placeholders and replace them with real logic.
             |2. Make sure the function returns a value in all cases.
             |3. Try one tiny example and compare it to the expected behavior.
             |""".stripMargin
        case DecisionLayer.IssueType.BOUNDARY_CONDITION =>
          s"""The primary test "$firstTestName" suggests a boundary/edge case.
             |1. Check empty/small inputs, negative values, or the 1-element case.
             |2. Verify initialization (first element vs fixed start value) and comparisons.
             |3. Compare expected/actual and adjust logic for that specific edge case.
             |""".stripMargin
        case DecisionLayer.IssueType.EXCEPTION_TYPE =>
          s"""Your code seems to crash with an exception in "$firstTestName".
             |1. Read the error type (Type/Index/Key/etc.) and find where the wrong type/index happens.
             |2. Re-check assumptions about inputs (empty list, None, wrong types).
             |3. Make that access/conversion robust and re-run the tests.
             |""".stripMargin
        case DecisionLayer.IssueType.PERFORMANCE =>
          s"""The test "$firstTestName" hints at a performance issue.
             |1. Look for unnecessary nested loops or repeated scans over the same data.
             |2. Consider whether you can do it in a single pass.
             |3. Re-run and confirm the timeout is gone.
             |""".stripMargin
        case _ =>
          s"""Ok, let's narrow down "$firstTestName"${functionNameHint.map(fn => s" (focus: $fn)").getOrElse("")}.
             |1. Check how you initialize the result (e.g., first element vs a fixed starting value).
             |2. Iterate over all elements and update the result only when you find a better candidate.
             |3. Quickly sanity-check edge cases against expected.
             |""".stripMargin
      }
  }
}
