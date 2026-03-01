package interactionPlugins.blockEnvironment.feedback.ai

import contentmanagement.model.language.{AppLanguage, HumanLanguage}
import interactionPlugins.blockEnvironment.feedback.diagnosis.{Diagnosis, DiagnosisJson}
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

    private val ExceptionType: IssueModule = (humanLanguage, signals) =>
      val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
      detectUnavailableModule(runtimeErr) match
        case Some((modName, Some(alt))) =>
          if humanLanguage == AppLanguage.German then
            Seq(s"Das Modul '$modName' ist in dieser Lernumgebung nicht verfügbar. Verwende stattdessen das eingebaute Modul '$alt' — es bietet dieselbe Funktionalität ohne zusätzliche Installation.")
          else
            Seq(s"The module '$modName' is not available in this learning environment. Use the built-in '$alt' module instead — it provides the same functionality without any installation.")
        case Some((modName, None)) =>
          if humanLanguage == AppLanguage.German then
            Seq(s"Das Modul '$modName' ist in dieser Lernumgebung nicht verfügbar. Finde einen alternativen Weg mit den eingebauten Python-Modulen oder der Standardbibliothek.")
          else
            Seq(s"The module '$modName' is not available in this learning environment. Find an alternative approach using Python built-ins or the standard library.")
        case None =>
          if humanLanguage == AppLanguage.German then Seq("Fokus: Exception-Typ verstehen und den fehlerhaften Zugriff robust machen.")
          else Seq("Focus: understand the exception type and make the failing access robust.")

    private val Incomplete: IssueModule = (humanLanguage, signals) =>
      val identityParam = detectIdentityReturn(signals.rawPython)
      if humanLanguage == AppLanguage.German then
        identityParam match
          case Some(param) =>
            Seq(
              s"KRITISCH: Die Funktion gibt den Parameter '$param' unverändert zurück — die gesamte Kernlogik fehlt.",
              "Fokus: Erkläre, was die Funktion konkret tun soll (Transformation, nicht Rückgabe).",
              "Gehe NICHT auf Randfälle (leere Liste, k > Länge usw.) ein, bevor die Grundfunktion existiert."
            )
          case None =>
            Seq(s"Fokus: unvollständige Implementierung (pass=${signals.hasPassStatement}).")
      else
        identityParam match
          case Some(param) =>
            Seq(
              s"CRITICAL: the function returns parameter '$param' unchanged — the entire core logic is absent.",
              "Focus: explain concretely what the function is supposed to do (the transformation), not a return.",
              "Do NOT mention edge cases (empty list, k > length, etc.) until the fundamental logic exists."
            )
          case None =>
            Seq(s"Focus: incomplete implementation (pass=${signals.hasPassStatement}).")

    private val Performance: IssueModule = (humanLanguage, _) =>
      if humanLanguage == AppLanguage.German then Seq("Fokus: unnötige Wiederholungen/verschachtelte Schleifen reduzieren.")
      else Seq("Focus: reduce unnecessary repeats / nested loops.")

    private val CompileError: IssueModule = (humanLanguage, signals) =>
      val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
      val errShort = if runtimeErr.length <= 300 then runtimeErr else runtimeErr.take(300) + "\u2026"
      if humanLanguage == AppLanguage.German then
        Seq(
          "Fokus: Syntaxfehler oder Einrückungsfehler. Sei präzise darüber, welche Zeile oder welches Konstrukt den Fehler verursacht.",
          s"Fehlertext vom System: ${if errShort.nonEmpty then errShort else "<nicht verfügbar>"}",
          "WICHTIG: Das System hat den Code bereits ausgeführt – fordere den Studierenden NICHT auf, den Code selbst auszuführen, zu starten oder den Fehler durch Ausführen zu finden."
        )
      else
        Seq(
          "Focus: syntax or indentation error. Be precise: name the specific line or construct that causes the error.",
          s"Error text from the system: ${if errShort.nonEmpty then errShort else "<none>"}",
          "IMPORTANT: the system has already run the code. Do NOT tell the student to run the code, execute it, or check where the error is by running it."
        )

    def forIssue(issueType: DecisionLayer.IssueType): IssueModule = issueType match {
      case DecisionLayer.IssueType.FORMAT_OUTPUT => FormatOutput
      case DecisionLayer.IssueType.IO_CONTRACT => IoContract
      case DecisionLayer.IssueType.BOUNDARY_CONDITION => Boundary
      case DecisionLayer.IssueType.EXCEPTION_TYPE => ExceptionType
      case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION => Incomplete
      case DecisionLayer.IssueType.PERFORMANCE => Performance
      case DecisionLayer.IssueType.COMPILE_ERROR => CompileError
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

  // Known third-party modules unavailable in Pyodide and their built-in alternatives.
  private val moduleAlternatives: Map[String, Option[String]] = Map(
    "regex"    -> Some("re"),
    "numpy"    -> None,
    "pandas"   -> None,
    "requests" -> None,
    "scipy"    -> None,
    "sklearn"  -> None,
    "matplotlib" -> None
  )

  // Parses "ModuleNotFoundError: No module named 'X'" from a runtime error string.
  // Returns (moduleName, alternativeOpt) if matched.
  private def detectUnavailableModule(runtimeErr: String): Option[(String, Option[String])] =
    val re = """(?i)ModuleNotFoundError.*No module named '([^']+)'""".r
    re.findFirstMatchIn(runtimeErr).map { m =>
      val modName = m.group(1).split("\\.").head // e.g. "regex.core" -> "regex"
      modName -> moduleAlternatives.getOrElse(modName, None)
    }

  private def detectIdentityReturn(rawPython: String): Option[String] = {
    val code = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    val lines = code.split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)
    // Extract parameter names from the first function definition.
    val funcDefRe = "(?i)def\\s+\\w+\\s*\\((\\w+)(?:\\s*,\\s*\\w+)*\\)\\s*:".r
    val paramsOpt = lines
      .flatMap { l =>
        funcDefRe.findFirstMatchIn(l).map { m =>
          // Pull all param names from the raw match: "def f(xs, k):" -> ["xs", "k"]
          val raw = m.group(0)
          val inner = raw.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
          inner.split(",").toSeq.map(_.trim).filter(_.nonEmpty)
        }
      }
      .headOption
    paramsOpt.flatMap { params =>
      params.find { param =>
        val returnRe = s"(?i)^return\\s+${scala.util.matching.Regex.quote(param)}\\s*$$".r
        val isReturned = lines.exists(l => returnRe.findFirstIn(l).nonEmpty)
        // Only flag as identity return if the param is never reassigned in the body.
        // e.g. "s = s.strip()" is a reassignment, not a pass-through.
        val isReassigned = lines.exists { l =>
          val assignRe = s"^${scala.util.matching.Regex.quote(param)}\\s*[+\\-*/|&^]?=".r
          assignRe.findFirstIn(l).nonEmpty
        }
        isReturned && !isReassigned
      }
    }
  }

  private def extractCodeObservations(rawPython: String): Seq[String] = {
    val code = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    val lines = code.split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)

    def firstMatch(re: scala.util.matching.Regex): Option[String] =
      lines.collectFirst { case l if re.findFirstIn(l).nonEmpty => l }

    val obs = scala.collection.mutable.ArrayBuffer.empty[String]

    // Highest-priority: identity return — the student returns a parameter with NO transformation.
    // This means the core logic is entirely absent. Signal this prominently.
    detectIdentityReturn(rawPython).foreach { param =>
      obs += s"CRITICAL: the function contains 'return $param' — it returns the input parameter unchanged with zero transformation. The core algorithm is completely missing. Do NOT discuss edge cases; focus entirely on the missing logic."
    }

    // Only add operator observations if no identity return was already flagged.
    if obs.isEmpty then {
      firstMatch("(?i)^return\\s+.*\\s-\\s.*$".r).foreach(_ => obs += "I see a return expression using '-' (subtraction).")
      firstMatch("(?i)^return\\s+.*\\s\\+\\s.*$".r).foreach(_ => obs += "I see a return expression using '+' (addition).")
      firstMatch("(?i)\\bif\\s+\\w+\\s<\\s\\w+\\s*:$".r).foreach(_ => obs += "I see a '<' comparison in an update condition inside a block.")
      firstMatch("(?i)\\bif\\s+\\w+\\s>\\s\\w+\\s*:$".r).foreach(_ => obs += "I see a '>' comparison in an update condition inside a block.")
    }

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
    diagnosis: Diagnosis,
    decision: DecisionLayer.Decision,
    humanLanguage: HumanLanguage,
    visibleTestNames: Seq[String],
    exerciseText: String,
    rawPython: String,
    isScriptExercise: Boolean = false
  ): Prompt = {
    val failedTests =
      signals.runtimeOutcome.tests
        .filterNot(_.passed)
        .filter(t => visibleTestNames.contains(t.name))

    val allTestsPassed =
      signals.runtimeOutcome.tests.nonEmpty &&
        signals.runtimeOutcome.tests.forall(_.passed)

    val testNames = failedTests.map(_.name).distinct.take(5)
    val primaryTestName = testNames.headOption

    val functionNameHint = extractFunctionNameHint(signals, testNames)
    val singleClearIssue = diagnosis.secondaryIssues.isEmpty && failedTests.size <= 1
    val baseConstraints = OutputConstraints(
      maxWords = 80,
      requireMentionedTestName = false,
      forbidCodeBlocks = true,
      forbidMarkdown = true,
      forbidBackticks = true,
      forbidChitchat = true,
      forbidProvidingFullSolution = true,
      minSteps =
        if allTestsPassed then 0
        else if singleClearIssue then 1
        else 2,
      maxSteps =
        if allTestsPassed then 2
        else if singleClearIssue then 2
        else
          decision.primaryIssue match {
            case DecisionLayer.IssueType.COMPILE_ERROR => 3
            case DecisionLayer.IssueType.PERFORMANCE   => 4
            case _                                     => 4
          },
      issueTypeHint = Some(decision.primaryIssue)
    )

    def adaptiveConstraints(
      base: OutputConstraints,
      lang: HumanLanguage,
      issue: DecisionLayer.IssueType
    ): OutputConstraints =
      val langBoost = if lang == AppLanguage.German then 10 else 0
      val issueBoost = issue match
        case DecisionLayer.IssueType.COMPILE_ERROR => 10
        case DecisionLayer.IssueType.EXCEPTION_TYPE => 10
        case DecisionLayer.IssueType.FORMAT_OUTPUT => 6
        case DecisionLayer.IssueType.IO_CONTRACT => 6
        case _ => 0

      val allowBackticks = lang == AppLanguage.German || issue == DecisionLayer.IssueType.EXCEPTION_TYPE || issue == DecisionLayer.IssueType.COMPILE_ERROR

      base.copy(
        maxWords = base.maxWords + langBoost + issueBoost,
        forbidBackticks = base.forbidBackticks && !allowBackticks
      )

    val constraints = adaptiveConstraints(baseConstraints, humanLanguage, decision.primaryIssue)

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

    val diagnosisJson =
      try DiagnosisJson.toJsonString(diagnosis)
      catch case _: Throwable => "{}"

    val recommendedChecks =
      if diagnosis.recommendedNextChecks.isEmpty then "<none>"
      else diagnosis.recommendedNextChecks.take(6).map(s => s"- $s").mkString("\n")

    val studentUsesFunction = rawPython.linesIterator.exists(l => l.trim.startsWith("def "))
    val isScript = isScriptExercise && !studentUsesFunction
    val scriptNoteDE =
      if isScript then
        "\nWICHTIG: Der eingereichte Code enthält keine Funktion (kein def) — es ist ein Skript. " +
        "Verwende NIEMALS das Wort \u201eFunktion\u201c oder \u201edeine Funktion\u201c. " +
        "Sprich stattdessen vom \u201eCode\u201c oder \u201eSkript\u201c.\n"
      else ""
    val scriptNoteEN =
      if isScript then
        "\nIMPORTANT: The submitted code contains no function definition (no def) — it is a script. " +
        "NEVER use the word 'function' or 'your function'. " +
        "Refer to 'the code' or 'the script' instead.\n"
      else ""
    val perspectiveExampleDE =
      if isScript then "\u201eDein Code liefert aktuell X, soll aber Y ergeben.\u201c"
      else "\u201eDeine Funktion gibt aktuell X zur\u00fcck, soll aber Y zur\u00fcckgeben.\u201c"
    val perspectiveExampleEN =
      if isScript then "\"Your code currently produces X but should produce Y.\""
      else "\"Your function currently returns X but should return Y.\""
    val noTestStepDE =
      if isScript then "kein \u201eTestedeinen Code mit ...\u201c, \u201e\u00dcberpr\u00fcfe, ob dein Code ... liefert\u201c"
      else "kein \u201eTeste deine Funktion mit ...\u201c, \u201e\u00dcberpr\u00fcfe, ob deine Funktion ... zur\u00fcckgibt\u201c"
    val noTestStepEN =
      if isScript then "no \"Test your code with ...\", \"Verify that your code produces ...\""
      else "no \"Test your function with ...\", \"Verify that your function returns ...\""
    val functionHintDE =
      if isScript then "Hinweis zum Code:\n- Skript-Aufgabe (kein Funktionsname)"
      else s"Hinweis zur Funktion:\n- functionName: ${functionNameHint.getOrElse("<unknown>")}"
    val functionHintEN =
      if isScript then "Code hint:\n- Script exercise (no function name)"
      else s"Function hint:\n- functionName: ${functionNameHint.getOrElse("<unknown>")}"

    val stepRuleDE =
      if constraints.minSteps <= 0 then
        "- Wenn es nur einen klaren Punkt gibt, antworte kurz und direkt (ein kurzer Absatz oder maximal 1–2 nummerierte Schritte)."
      else
        s"- Gib ${constraints.minSteps}–${constraints.maxSteps} konkrete Schritte (als nummerierte Liste)."
    val stepRuleEN =
      if constraints.minSteps <= 0 then
        "- If there is only one clear point, answer briefly and directly (one short paragraph or at most 1–2 numbered steps)."
      else
        s"- Provide ${constraints.minSteps}–${constraints.maxSteps} concrete steps (numbered list)."

    val passModeRuleDE =
      if allTestsPassed then
        "- WICHTIG (alle Tests grün): Gib höchstens EINEN konkreten Verbesserungsaspekt, der im Code direkt sichtbar ist. Behaupte NICHT \"effizient\" oder \"optimal\", wenn das nicht klar aus dem Code belegbar ist. Behaupte NICHT dass Randfälle fehlen (z.B. Type-Checks, None-Handling, Validierung), wenn kein Test diese Fälle prüft und die Aufgabe nicht explizit danach fragt. Unterscheide klar zwischen ungenutztem Code (dead code, z.B. Variablen die deklariert aber nie verwendet werden) und Code der das Ergebnis tatsächlich beeinflusst. Wenn der Code KORREKT funktioniert aber ungenutzten Code enthält, sage explizit dass die Lösung korrekt ist (z.B. \"Dein Code liefert das richtige Ergebnis\" oder \"Deine Ausgabe ist korrekt\"), und dann \"du könntest Zeile X entfernen\" oder \"du könntest aufräumen\". Behaupte NICHT dass das Verhalten nicht-deterministisch ist oder unterschiedliche Ergebnisse liefert, wenn der ungenutzte Code die Ausgabe/das return-Statement nicht beeinflusst. Wenn es NICHTS konkretes zu verbessern gibt im sichtbaren Code, gib einfach ein kurzes Lob ohne generische Verbesserungsvorschläge."
      else ""
    val passModeRuleEN =
      if allTestsPassed then
        "- IMPORTANT (all tests green): Give at most ONE concrete improvement point that is directly visible in the code. Do NOT claim \"efficient\" or \"optimal\" unless that is clearly evidenced by the code. Do NOT claim that edge cases are missing (e.g., type checks, None handling, validation) if no test checks those cases and the task doesn't explicitly ask for them. Distinguish clearly between unused code (dead code, e.g., variables declared but never used) and code that actually affects the result. If the code works CORRECTLY but contains unused code, explicitly state the solution is correct (e.g., \"Your code produces the right result\" or \"Your output is correct\"), then say \"you could remove line X\" or \"you could clean up\". Do NOT claim the behavior is non-deterministic or produces different results if the unused code doesn't affect the output/return statement. If there is NOTHING concrete to improve in the visible code, just give brief praise without generic improvement suggestions."
      else ""

    val instruction =
      val focus = IssueModule.forIssue(decision.primaryIssue).focusLines(humanLanguage, signals).mkString("\n")
      if isGerman(humanLanguage) then
        s"""Du bist ein Tutor. Gib eine kurze, konkrete Hilfestellung.$scriptNoteDE
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
          |- Verwende NIEMALS Formulierungen wie „Der Test erwartet …", „die Tests erwarten …" oder ähnliche testzentrierte Sprache. Formuliere stattdessen direkt aus Sicht des Verhaltens: was der Code aktuell tut und was er stattdessen tun soll (z.B. $perspectiveExampleDE).
          |- Erwähne AUSSCHLIESSLICH Probleme, die im bereitgestellten Code-Ausschnitt direkt sichtbar ODER durch die Fehlermeldung/Testergebnisse eindeutig belegt sind. Nenne KEINE allgemeinen Hygiene-Hinweise zu Einrückung, Klammern, Anführungszeichen oder Syntaxdetails, wenn der konkrete Fehler das nicht zeigt.
           |$stepRuleDE
           |- Keine Lösung ausformulieren, kein vollständiger Code, keine Codeblöcke.
          |- Schritte müssen zum konkreten Verhalten passen (keine generischen Tipps).
           |- Nenne Begriffe wie „Dictionary“, „Set“, „verschachtelte Schleifen“ oder ähnliche Strukturhinweise NUR, wenn diese Strukturen im sichtbaren Code wirklich vorhanden sind.
           |- Weise den Studierenden NICHT an, den Code auszuführen oder zu starten, um den Fehler zu finden – das System führt alle Tests bereits aus.
           |- Füge KEINEN Schritt hinzu, der den Studierenden auffordert, den Code zu testen, zu überprüfen oder zu bestätigen (z.B. $noTestStepDE, „Stelle sicher, dass der Code korrekt funktioniert"). Das System testet vollautomatisch – solche Schritte sind inhaltsleer.
           |- Wenn die Kernlogik fehlt (z.B. Identity-Return), sprich NUR über die fehlende Grundimplementierung. Keine Randfälle, keine Fehlerbehandlung.
           |- Nenne KEINE konkreten Operatoren, Methodenaufrufe oder Ausdrücke, die der Kern der Lösung sind (z.B. keinen Modulo-Operator nennen).
           |- Du musst NICHT immer expected/actual zitieren. Wenn es hilft, formuliere es natürlich: „Der Test erwartet X, aber dein Code liefert Y.“
          |- Du darfst genau ein sehr kurzes Code-Fragment zitieren (max. 1 Zeile), um die Ursache zu benennen. Keine korrigierte Version zeigen.
           |$passModeRuleDE
           |
           |$functionHintDE
           |
           |Issue-Fokus:
           |$focus
           |
           |Routing:
           |- primaryIssue: ${diagnosis.primaryIssue}
           |- secondaryIssues: ${diagnosis.secondaryIssues.mkString(", ")}
           |- severity: ${diagnosis.severity}
           |- confidence: ${diagnosis.confidence}
           |- taskProfile.tags: ${diagnosis.taskProfile.tags.mkString(", ")}
           |
           |Diagnosis JSON (authoritative; do NOT copy verbatim to student):
           |$diagnosisJson
           |
           |Recommended checks (use as ideas; do not list all):
           |$recommendedChecks
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
          s"""You are a tutor. Provide a short, concrete hint.$scriptNoteEN
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
            |- NEVER write "the test expects", "the tests expect", or any test-centric phrasing. Describe directly from the behavior's perspective: what the code currently does and what it should do instead (e.g., $perspectiveExampleEN).
            |- ONLY mention issues that are directly visible in the code snippet OR directly evidenced by the runtime error / failing cases. Do NOT list generic hygiene tips (indentation consistency, bracket matching, parentheses, quote matching) unless the error message or code explicitly shows that specific issue.
           |$stepRuleEN
           |- Do not provide the full solution, no full code, no code blocks.
            |- Steps must match the observed behavior (avoid generic advice).
           |- Mention terms like "dictionary", "set", "nested loops", or similar structural advice ONLY if those structures are actually visible in the provided code.
           |- Do NOT tell the student to "run the code", "execute it", or "see where the error is by running" — the system already runs all tests.
           |- Do NOT add a step that tells the student to test, verify, or confirm their code (e.g., $noTestStepEN, "Make sure the code behaves as expected"). The system tests automatically — such steps are empty filler.
           |- If the core logic is absent (e.g., identity return), address ONLY the missing transformation. No edge cases, no error handling.
           |- Do NOT name specific operators, method calls, or expressions that are the heart of the solution (e.g., do not say "use %" or "use len()").
           |- You do NOT have to quote expected/actual. If helpful, phrase it naturally: “The test expects X, but your code produces Y.”
            |- You may quote exactly one short code fragment (max 1 line) to point to the cause. Do not show a corrected version.
           |$passModeRuleEN
           |
           |$functionHintEN
           |
           |Issue focus:
           |$focus
           |
           |Routing:
           |- primaryIssue: ${diagnosis.primaryIssue}
           |- secondaryIssues: ${diagnosis.secondaryIssues.mkString(", ")}
           |- severity: ${diagnosis.severity}
           |- confidence: ${diagnosis.confidence}
           |- taskProfile.tags: ${diagnosis.taskProfile.tags.mkString(", ")}
           |
           |Diagnosis JSON (authoritative; do NOT copy verbatim to student):
           |$diagnosisJson
           |
           |Recommended checks (use as ideas; do not list all):
           |$recommendedChecks
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
    humanLanguage: HumanLanguage,
    isScriptExercise: Boolean = false,
    configuredTestNames: Seq[String] = Seq.empty
  ): String = {
    val failing = signals.runtimeOutcome.tests.filterNot(_.passed)
    val firstTestName = failing.headOption.map(_.name).getOrElse(if isGerman(humanLanguage) then "deine Abgabe" else "your submission")
    val functionNameHint = extractFunctionNameHint(signals, failing.map(_.name).distinct.take(5))
    val hasDef = signals.rawPython.linesIterator.exists(l => l.trim.startsWith("def "))
    val isScript = isScriptExercise && !hasDef
    val noFunctionYet = !isScriptExercise && !hasDef
    val meaningfulLineCount = signals.rawPython.linesIterator.count(l => l.trim.nonEmpty && !l.trim.startsWith("#"))
    val trivialScriptSubmission = isScriptExercise && meaningfulLineCount < 2

    if trivialScriptSubmission then
      if isGerman(humanLanguage) then
        s"""Deine Abgabe ist noch zu kurz, um die Aufgabe zu lösen.
           |1. Lies die Aufgabenstellung noch einmal durch.
           |2. Beginne mit der eigentlichen Logik, z.B. einer Variablenzuweisung oder einer Schleife.
           |3. Baue das Skript schrittweise auf und prüfe zwischendurch, ob es das Richtige tut.
           |""".stripMargin
      else
        s"""Your submission is too short to solve the task yet.
           |1. Re-read the exercise statement.
           |2. Start with the actual logic, e.g. a variable assignment or a loop.
           |3. Build the script step by step and check that it does the right thing.
           |""".stripMargin
    else if noFunctionYet then
      val nameErrorRe = """NameError: name '([a-zA-Z_]\w*)' is not defined""".r
      val allTests = signals.runtimeOutcome.tests
      val candidateNames = allTests.map(_.name) ++ configuredTestNames
      val fnFromTestName = candidateNames.headOption
        .map(_.takeWhile(c => c != '_' && c != '-'))
        .filter(n => n.length >= 2 && n.forall(c => c.isLetterOrDigit || c == '_'))
      val fnFromError = failing
        .flatMap(t => t.actual.split("\n").toSeq)
        .flatMap(line => nameErrorRe.findFirstMatchIn(line).map(_.group(1)))
        .find(n => fnFromTestName.forall(prefix => n.startsWith(prefix) || prefix.startsWith(n.take(3))))
      val fnName = functionNameHint
        .orElse(fnFromTestName)
        .orElse(fnFromError)
        .getOrElse(if isGerman(humanLanguage) then "deine_funktion" else "your_function")
      if isGerman(humanLanguage) then
        s"""Deine Abgabe enthält noch keine Funktion.
           |1. Definiere die Funktion mit def $fnName(...): am Anfang.
           |2. Schreibe die Logik eingerückt in den Funktionskörper.
           |3. Gib das Ergebnis mit return am Ende der Funktion zurück.
           |""".stripMargin
      else
        s"""Your submission doesn't define any function yet.
           |1. Start with def $fnName(...): at the top.
           |2. Write your logic indented inside the function body.
           |3. Return the result with return at the end of the function.
           |""".stripMargin
    else if isGerman(humanLanguage) then
      decision.primaryIssue match {
        case DecisionLayer.IssueType.FORMAT_OUTPUT =>
          s"""Okay, das wirkt wie ein Ausgabe/Format-Thema bei "$firstTestName".
             |1. Schau dir im Test expected vs actual an (Whitespace/Zeilenumbrüche sind oft der Grund).
             |2. Entferne zusätzliche Prints oder mache die Ausgabe exakt passend (printCount=${signals.printCount}).
             |3. Prüfe, dass du wirklich nur das ausgibst, was der Test erwartet.
             |""".stripMargin
        case DecisionLayer.IssueType.IO_CONTRACT =>
          val pureHintDE = if isScript then "Tests erwarten oft reinen Code ohne input()-Aufruf." else "Tests erwarten oft eine pure Funktion ohne Eingabe."
          val returnHintDE = if isScript then "Wenn du etwas ausgibst: stelle sicher, dass der Code stattdessen den Wert liefert (oder dass die Ausgabe exakt passt)." else "Wenn du etwas ausgibst: stelle sicher, dass die Funktion stattdessen den Wert zurückgibt (oder dass die Ausgabe exakt passt)."
          s"""Der Test "$firstTestName" sieht nach einem I/O-Vertragsthema aus.
             |1. Prüfe, ob du irgendwo input() verwendest (inputCallCount=${signals.inputCallCount}) — $pureHintDE
             |2. $returnHintDE
             |3. Vergleiche expected vs actual und entferne alles, was nicht gefordert ist.
             |""".stripMargin
        case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION =>
          val returnsHintDE = if isScript then "Stelle sicher, dass dein Code für alle Fälle wirklich ein Ergebnis liefert." else "Stelle sicher, dass deine Funktion für alle Fälle wirklich ein Ergebnis zurückgibt."
          s"""Bei "$firstTestName" wirkt es so, als ob die Implementierung noch nicht vollständig ist.
             |1. Suche nach pass/TODO-Platzhaltern und ersetze sie durch echte Logik.
             |2. $returnsHintDE
             |3. Teste mit einem kleinen Beispiel und vergleiche dann mit expected.
             |""".stripMargin
        case DecisionLayer.IssueType.BOUNDARY_CONDITION =>
          s"""Der primäre Test "$firstTestName" deutet auf einen Grenzfall hin.
             |1. Prüfe leere/kleine Eingaben, negative Werte oder genau-1-Element-Fälle.
             |2. Kontrolliere Initialisierung (erstes Element vs fester Startwert) und Vergleichsoperatoren.
             |3. Vergleiche expected/actual und passe die Logik für genau diesen Grenzfall an.
             |""".stripMargin
        case DecisionLayer.IssueType.EXCEPTION_TYPE =>
          val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
          val testErr = signals.runtimeOutcome.tests.flatMap(t => Seq(t.actual)).mkString("\n")
          detectUnavailableModule(runtimeErr + "\n" + testErr) match
            case Some((mod, Some(alt))) =>
              s"""Das Modul '$mod' ist in dieser Lernumgebung nicht verf\u00fcgbar.
                 |1. \u00c4ndere den Import zu 'import $alt'.
                 |2. Passe die weiteren Aufrufe entsprechend an (die API von '$alt' ist sehr \u00e4hnlich).
                 |3. Teste erneut.
                 |""".stripMargin
            case Some((mod, None)) =>
              s"""Das Modul '$mod' ist in dieser Lernumgebung nicht verf\u00fcgbar.
                 |1. Suche nach einer Alternative mit eingebauten Python-Modulen oder der Standardbibliothek.
                 |2. Passe den Import und die Aufrufe entsprechend an.
                 |3. Teste erneut.
                 |""".stripMargin
            case None =>
              s"""Bei "$firstTestName" scheint dein Code mit einer Exception abzubrechen.
                 |1. Lies die Fehlermeldung (Type/Index/Key/etc.) und finde die Stelle, wo der falsche Typ/Index entsteht.
                 |2. Pr\u00fcfe Annahmen \u00fcber Eingaben (leere Liste, None, falscher Datentyp).
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
          val pureHintEN = if isScript then "many tests expect no input() calls — keep the code self-contained." else "many tests expect a pure function without reading input."
          val returnHintEN = if isScript then "If you print anything, confirm the task expects printing; otherwise make the code produce the value directly." else "If you print anything, confirm the task expects printing; otherwise return the value."
          s"""The test "$firstTestName" looks like an I/O contract issue.
             |1. Check whether you call input() (inputCallCount=${signals.inputCallCount}) — $pureHintEN
             |2. $returnHintEN
             |3. Compare expected vs actual and remove anything not required.
             |""".stripMargin
        case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION =>
          val returnsHintEN = if isScript then "Make sure the code produces a value in all cases." else "Make sure the function returns a value in all cases."
          s"""For "$firstTestName" it looks like the implementation is incomplete.
             |1. Look for pass/TODO placeholders and replace them with real logic.
             |2. $returnsHintEN
             |3. Try one tiny example and compare it to the expected behavior.
             |""".stripMargin
        case DecisionLayer.IssueType.BOUNDARY_CONDITION =>
          s"""The primary test "$firstTestName" suggests a boundary/edge case.
             |1. Check empty/small inputs, negative values, or the 1-element case.
             |2. Verify initialization (first element vs fixed start value) and comparisons.
             |3. Compare expected/actual and adjust logic for that specific edge case.
             |""".stripMargin
        case DecisionLayer.IssueType.EXCEPTION_TYPE =>
          val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
          val testErr = signals.runtimeOutcome.tests.flatMap(t => Seq(t.actual)).mkString("\n")
          detectUnavailableModule(runtimeErr + "\n" + testErr) match
            case Some((mod, Some(alt))) =>
              s"""The module '$mod' is not available in this learning environment.
                 |1. Change the import to 'import $alt'.
                 |2. Adjust the calls accordingly (the '$alt' API is very similar).
                 |3. Re-run the tests.
                 |""".stripMargin
            case Some((mod, None)) =>
              s"""The module '$mod' is not available in this learning environment.
                 |1. Find an alternative using Python built-ins or the standard library.
                 |2. Update the import and any related calls.
                 |3. Re-run the tests.
                 |""".stripMargin
            case None =>
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
