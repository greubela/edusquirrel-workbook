package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis

import it.evadid.core.datastructures.language.AppLanguage

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{BlockFeedbackRequest, PythonTestResult}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules.RuleResult
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service.BlockFeedbackTestPlan

object DiagnosisEngine:

  def build(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision
  ): Diagnosis =
    val taskProfile = inferTaskProfile(request, plan, signals, decision)

    val evidence = collectEvidence(plan, signals, decision)

    val category = categoryFor(decision.primaryIssue)

    val hypotheses =
      val primary =
        DiagnosisHypothesis(
          issue = decision.primaryIssue,
          confidence = clamp01(decision.confidence),
          rationale = decision.topCauses.mkString("; ")
        )
      val secondary =
        decision.secondaryIssues.zipWithIndex.map { case (issue, idx) =>
          DiagnosisHypothesis(
            issue = issue,
            confidence = clamp01(decision.confidence - (0.08 * (idx + 1))),
            rationale = "Secondary routing hypothesis"
          )
        }
      (primary +: secondary).distinctBy(_.issue).take(3)

    val nextChecks = recommendNextChecks(request.humanLanguage, signals, decision, taskProfile)

    Diagnosis(
      category = category,
      primaryIssue = decision.primaryIssue,
      secondaryIssues = decision.secondaryIssues.distinct.take(3),
      severity = decision.severity,
      confidence = clamp01(decision.confidence),
      hypotheses = hypotheses,
      evidence = evidence,
      taskProfile = taskProfile,
      recommendedNextChecks = nextChecks
    )

  private def categoryFor(issue: DecisionLayer.IssueType): DiagnosisCategory =
    issue match
      case DecisionLayer.IssueType.CORRECT       => DiagnosisCategory.Logic
      case DecisionLayer.IssueType.COMPILE_ERROR => DiagnosisCategory.Syntax
      case DecisionLayer.IssueType.EXCEPTION_TYPE => DiagnosisCategory.Runtime
      case DecisionLayer.IssueType.API_SIGNATURE => DiagnosisCategory.Spec
      case DecisionLayer.IssueType.IO_CONTRACT => DiagnosisCategory.Spec
      case DecisionLayer.IssueType.FORMAT_OUTPUT => DiagnosisCategory.Spec
      case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION => DiagnosisCategory.Logic
      case DecisionLayer.IssueType.BOUNDARY_CONDITION => DiagnosisCategory.Logic
      case DecisionLayer.IssueType.LOGIC_EDGE_CASE => DiagnosisCategory.Logic
      case DecisionLayer.IssueType.PERFORMANCE => DiagnosisCategory.Performance
      case DecisionLayer.IssueType.NONDETERMINISM => DiagnosisCategory.Logic

  private def inferTaskProfile(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision
  ): TaskProfile =
    val statement = request.exerciseText.getInLanguage(request.humanLanguage).replace("\r\n", "\n").toLowerCase
    val allTestsCode = (plan.visibleTests.map(_.code) ++ plan.hiddenTests.map(_.code)).mkString("\n").toLowerCase

    def containsAny(hay: String, needles: Seq[String]): Boolean = needles.exists(hay.contains)

    val tags =
      val b = Seq.newBuilder[String]
      if containsAny(statement + "\n" + allTestsCode, Seq("list", "array", "xs", "nums")) then b += "list"
      if containsAny(statement + "\n" + allTestsCode, Seq("string", "substring", "chars", "s=")) then b += "string"
      if containsAny(statement + "\n" + allTestsCode, Seq("bracket", "parenthes", "stack", "[]", "{}", "()")) then b += "brackets"
      if containsAny(statement + "\n" + allTestsCode, Seq("index", "indices")) then b += "indices"
      if containsAny(statement + "\n" + allTestsCode, Seq("sum", "add", "target")) then b += "arithmetic"
      if signals.randomCallCount > 0 then b += "random"
      if signals.inputCallCount > 0 then b += "io"
      b.result().distinct

    val testsLookPure =
      allTestsCode.contains("assert") &&
        !containsAny(allTestsCode, Seq("input(", "sys.stdin", "stdin", "raw_input"))

    val expectsPrintOutput =
      containsAny(allTestsCode, Seq("capfd", "capsys", "stdout", "print(")) ||
        decision.primaryIssue == DecisionLayer.IssueType.FORMAT_OUTPUT

    val outputFormatSensitive =
      decision.primaryIssue == DecisionLayer.IssueType.FORMAT_OUTPUT ||
        signals.stdoutLineCount > 0 ||
        containsAny(allTestsCode, Seq("strip()", "splitlines", "\\n"))

    val requiresPureFunction = testsLookPure && !expectsPrintOutput
    val ioLikelyForbidden = requiresPureFunction

    TaskProfile(
      tags = tags,
      requiresPureFunction = requiresPureFunction,
      ioLikelyForbidden = ioLikelyForbidden,
      expectsReturnValue = !expectsPrintOutput,
      outputFormatSensitive = outputFormatSensitive
    )

  private def collectEvidence(
    plan: BlockFeedbackTestPlan,
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision
  ): Seq[EvidenceItem] =
    val visibleTestNames = plan.visibleTests.map(_.name).toSet

    val failingVisibleTests =
      signals.runtimeOutcome.tests
        .filterNot(_.passed)
        .filter(t => visibleTestNames.contains(t.name))
        .take(6)

    val testEvidence = failingVisibleTests.zipWithIndex.map { case (t, idx) =>
      EvidenceItem(
        id = s"visible_test_${idx}_${safeId(t.name)}",
        kind = EvidenceKind.VisibleTest,
        title = t.name,
        details = summarizeTestResult(t)
      )
    }

    val runtimeErr = signals.runtimeOutcome.runtimeError.map(_.trim).filter(_.nonEmpty).toSeq.map { msg =>
      EvidenceItem(
        id = "runtime_error",
        kind = EvidenceKind.RuntimeError,
        title = "Runtime error",
        details = truncate(msg, 600)
      )
    }

    val stderr = signals.runtimeOutcome.stderr.map(_.trim).filter(_.nonEmpty).toSeq.map { msg =>
      EvidenceItem(
        id = "stderr",
        kind = EvidenceKind.Stderr,
        title = "stderr",
        details = truncate(msg, 600)
      )
    }

    val stdout =
      // Only include stdout when it is likely relevant (formatting/prints)
      if signals.stdoutLineCount > 0 && signals.printCount > 0 then
        signals.runtimeOutcome.stdout.map(_.trim).filter(_.nonEmpty).toSeq.map { msg =>
          EvidenceItem(
            id = "stdout",
            kind = EvidenceKind.Stdout,
            title = "stdout",
            details = truncate(msg, 400)
          )
        }
      else Nil

    val ruleEvidence =
      (failedRules(signals.pythonRules).take(4).zipWithIndex.map { case (r, idx) =>
        EvidenceItem(
          id = s"py_rule_${idx}_${safeId(r.id)}",
          kind = EvidenceKind.StaticRule,
          title = s"${r.id} (${r.severity})",
          details = truncate(r.message + r.details.map(d => "\n" + d).getOrElse(""), 400)
        )
      } ++
        failedRules(signals.vmRules).take(4).zipWithIndex.map { case (r, idx) =>
          EvidenceItem(
            id = s"vm_rule_${idx}_${safeId(r.id)}",
            kind = EvidenceKind.VmRule,
            title = s"${r.id} (${r.severity})",
            details = truncate(r.message + r.details.map(d => "\n" + d).getOrElse(""), 400)
        )
      }).toSeq

    val signalEvidence =
      Seq(
        EvidenceItem(
          id = "signal_counts",
          kind = EvidenceKind.Signal,
          title = "Signals",
          details = s"printCount=${signals.printCount}; inputCallCount=${signals.inputCallCount}; randomCallCount=${signals.randomCallCount}; hasPass=${signals.hasPassStatement}; boundaryHintScore=${signals.boundaryHintScore}"
        )
      )

    val heuristicEvidence =
      decision.evidence.take(6).zipWithIndex.map { case (e, idx) =>
        EvidenceItem(
          id = s"router_evidence_${idx}_${safeId(e.key)}",
          kind = EvidenceKind.Heuristic,
          title = e.key,
          details = e.value
        )
      }

    (testEvidence ++ runtimeErr ++ stderr ++ stdout ++ ruleEvidence ++ signalEvidence ++ heuristicEvidence).distinctBy(_.id)

  private def summarizeTestResult(t: PythonTestResult): String =
    val msg = t.message.getOrElse("")
    val exp = truncate(t.expected.replace("\r\n", "\n"), 240)
    val act = truncate(t.actual.replace("\r\n", "\n"), 200)
    val msgShort = if msg.trim.isEmpty then "" else "\nmessage=" + truncate(msg.replace("\r\n", "\n"), 200)
    s"expected=${exp}\nactual=${act}${msgShort}".trim

  private def failedRules(rules: Seq[RuleResult]): Seq[RuleResult] =
    rules.filterNot(_.passed)

  private def recommendNextChecks(
    lang: HumanLanguage,
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision,
    profile: TaskProfile
  ): Seq[String] =
    val isGerman = lang == AppLanguage.German

    def steps(en: Seq[String], de: Seq[String]): Seq[String] = if isGerman then de else en

    decision.primaryIssue match
      case DecisionLayer.IssueType.COMPILE_ERROR =>
        steps(
          en = Seq(
            "Run the code once to see the exact SyntaxError/IndentationError line.",
            "Check indentation consistency (spaces only; no mixed tabs).",
            "Make sure the function has a body and parentheses/quotes are balanced."
          ),
          de = Seq(
            "Führe den Code einmal aus und schau auf die genaue Syntax/IndentationError-Zeile.",
            "Prüfe konsistente Einrückung (nur Spaces; keine gemischten Tabs).",
            "Stelle sicher, dass die Funktion einen Body hat und Klammern/Quotes geschlossen sind."
          )
        )

      case DecisionLayer.IssueType.API_SIGNATURE =>
        steps(
          en = Seq(
            "Verify the required function name and parameters match the task/tests.",
            "Ensure your function returns a value in all paths.",
            "Avoid relying on global variables; keep logic inside the function."
          ),
          de = Seq(
            "Prüfe Funktionsname und Parameter gegen Aufgabe/Tests.",
            "Stelle sicher, dass deine Funktion in allen Pfaden etwas zurückgibt.",
            "Vermeide globale Variablen; halte die Logik in der Funktion."
          )
        )

      case DecisionLayer.IssueType.IO_CONTRACT =>
        val extra =
          if profile.ioLikelyForbidden && signals.inputCallCount > 0 then
            steps(
              en = Seq("Remove input() calls; tests usually call your function directly."),
              de = Seq("Entferne input()-Aufrufe; Tests rufen die Funktion direkt auf.")
            )
          else Nil
        steps(
          en = Seq(
            "Ensure you follow the I/O contract: return values instead of printing unless explicitly required.",
            "Remove debug prints that change stdout.",
            "Re-run on the failing case and compare expected vs observed behavior."
          ),
          de = Seq(
            "Halte den I/O-Vertrag ein: gib Werte zurück statt zu printen (außer ausdrücklich gefordert).",
            "Entferne Debug-Prints, die stdout verändern.",
            "Teste am fehlschlagenden Fall und vergleiche erwartetes vs beobachtetes Verhalten."
          )
        ) ++ extra

      case DecisionLayer.IssueType.FORMAT_OUTPUT =>
        steps(
          en = Seq(
            "Make stdout exactly match the required format (whitespace/newlines matter).",
            "Remove any extra prints/logging.",
            "If you return a value, don't also print it (unless asked)."
          ),
          de = Seq(
            "Sorge dafür, dass stdout exakt dem geforderten Format entspricht (Whitespace/Zeilenumbrüche zählen).",
            "Entferne zusätzliche Prints/Logging.",
            "Wenn du etwas zurückgibst: nicht zusätzlich printen (außer gefordert)."
          )
        )

      case DecisionLayer.IssueType.EXCEPTION_TYPE =>
        steps(
          en = Seq(
            "Locate the exact line that raises the exception.",
            "Add guards for empty inputs / out-of-range indices / missing keys.",
            "Confirm types: print/inspect the types of intermediate values."
          ),
          de = Seq(
            "Finde die exakte Zeile, die die Exception auslöst.",
            "Baue Guards für leere Inputs / ungültige Indizes / fehlende Keys ein.",
            "Prüfe Typen: inspiziere Typen von Zwischenwerten."
          )
        )

      case DecisionLayer.IssueType.BOUNDARY_CONDITION =>
        steps(
          en = Seq(
            "Try edge cases: empty input, 1-element input, duplicates/extremes.",
            "Check initialization and comparison direction (< vs >).",
            "Trace the variables on the smallest failing example."
          ),
          de = Seq(
            "Teste Grenzfälle: leerer Input, 1-Element, Duplikate/Extremwerte.",
            "Prüfe Initialisierung und Vergleichsrichtung (< vs >).",
            "Trace Variablen am kleinsten fehlschlagenden Beispiel."
          )
        )

      case DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION =>
        steps(
          en = Seq(
            "Replace pass/TODO placeholders with real logic.",
            "Ensure all branches return a result.",
            "Run one tiny example by hand and check each intermediate step."
          ),
          de = Seq(
            "Ersetze pass/TODO-Platzhalter durch echte Logik.",
            "Stelle sicher, dass alle Zweige ein Ergebnis zurückgeben.",
            "Spiele ein kleines Beispiel per Hand durch und prüfe Zwischenschritte."
          )
        )

      case DecisionLayer.IssueType.PERFORMANCE =>
        steps(
          en = Seq(
            "Look for nested loops over the same data and reduce to one pass.",
            "Use a dictionary/set to avoid repeated scans.",
            "Re-run and confirm the timeout disappears."
          ),
          de = Seq(
            "Suche nach verschachtelten Schleifen über dieselben Daten und reduziere auf einen Durchlauf.",
            "Nutze dict/set, um wiederholte Scans zu vermeiden.",
            "Teste erneut und prüfe, ob der Timeout verschwindet."
          )
        )

      case DecisionLayer.IssueType.NONDETERMINISM =>
        steps(
          en = Seq(
            "Remove randomness; tests need deterministic output.",
            "If you must pick an element, define a deterministic tie-break.",
            "Re-run multiple times to confirm stable behavior."
          ),
          de = Seq(
            "Entferne Zufall; Tests brauchen deterministisches Verhalten.",
            "Wenn du etwas auswählen musst: definiere einen deterministischen Tie-Break.",
            "Führe mehrfach aus und prüfe Stabilität."
          )
        )

      case _ =>
        steps(
          en = Seq(
            "Pick the smallest failing case and trace your variables step-by-step.",
            "Compare expected vs observed behavior and identify the first divergence.",
            "Adjust the condition/update that causes the divergence and re-test."
          ),
          de = Seq(
            "Nimm den kleinsten fehlschlagenden Fall und trace Variablen Schritt für Schritt.",
            "Vergleiche erwartetes vs beobachtetes Verhalten und finde den ersten Unterschied.",
            "Passe genau die Bedingung/Update-Logik an und teste erneut."
          )
        )

  private def truncate(text: String, maxChars: Int): String =
    val t = Option(text).getOrElse("").trim
    if t.length <= maxChars then t else t.take(maxChars) + "…"

  private def safeId(text: String): String =
    Option(text).getOrElse("x").replaceAll("[^a-zA-Z0-9_-]", "_")

  private def clamp01(x: Double): Double =
    if x.isNaN then 0.0
    else if x < 0.0 then 0.0
    else if x > 1.0 then 1.0
    else x
