package interactionPlugins.blockEnvironment.feedback.ai

import interactionPlugins.blockEnvironment.feedback.ml.DecisionLayer

/**
 * Post-processing / validation for LLM output.
 * Ensures the text is short, test-related, and helps without giving a solution.
 */
object QualityGate {

  final case class GateResult(
    passed: Boolean,
    reasons: Seq[String],
    finalText: String
  )

  /**
   * Maximum number of times the LLM is asked to rewrite its own response when
   * QualityGate finds violations. Each retry uses a targeted correction prompt.
   */
  val maxRewriteAttempts: Int = 3

  /**
   * If true (default), the last attempt's best-effort-repaired text is passed through
   * to the student even when QualityGate still finds violations after all retries.
   *
   * This is strongly preferred over the deterministic fallback because an imperfect
   * LLM hint is almost always more contextually helpful than a generic template.
   *
   * Set to false only for strict enforcement where you accept the deterministic fallback.
   */
  val allowImperfectPassthrough: Boolean = true

  /**
   * Builds a precise correction prompt to send back to the LLM after a QualityGate
   * rejection.  The prompt:
   *   - Numbers each violated rule and explains exactly what was wrong and how to fix it
   *   - Includes the rejected response so the LLM can pinpoint the problems
   *   - Repeats the full original prompt so the LLM retains all exercise/student context
   *   - Gives an unambiguous rewrite instruction
   *
   * @param reasons        The reason codes returned by QualityGate.validate()
   * @param originalPrompt The complete original prompt that produced the bad response
   * @param rejectedResponse The raw LLM text that was rejected
   * @param constraints    The active OutputConstraints (used for step limits in messages)
   * @param attemptNr      1-based index of this rewrite attempt (used for framing only)
   */
  def buildCorrectionPrompt(
    reasons: Seq[String],
    originalPrompt: String,
    rejectedResponse: String,
    constraints: PromptTemplates.OutputConstraints,
    attemptNr: Int
  ): String = {

    def humanReadable(reason: String): String = reason match {
      case "empty" =>
        "Your response was empty. You must produce a non-empty, student-facing hint."

      case "missing_test_name" =>
        "You did not mention the failing test name anywhere in your response. " +
        "The test name must appear verbatim so the student knows which case is broken."

      case "contains_code_block" =>
        "You included a fenced code block (``` … ```). REMOVE it entirely. " +
        "NEVER show runnable Python — not even a partial snippet. " +
        "Describe every fix in plain words only."

      case "contains_backticks" =>
        "You used backtick characters (`). Remove ALL backticks. " +
        "Do not format any part of your response as code — not even a single character, " +
        "expression, or function name. Whenever you want to reference something specific " +
        "from the code, write it in plain prose without any formatting whatsoever."

      case "looks_like_markdown" =>
        "You used Markdown markup (**bold**, ##heading, __underline__). " +
        "This is plain-text feedback — strip every Markdown marker."

      case "looks_like_solution_code" =>
        "Your response contains a bare code line — a line beginning with 'def', 'return', " +
        "'class', 'import', or 'from'. Completely remove such lines. " +
        "NEVER quote or display runnable code, not even a single line. " +
        "Describe what is wrong and what to change entirely in plain prose. " +
        "For example, instead of writing the function signature, say: " +
        "'The first line of your function definition is missing a colon at the end.' " +
        "Keep every concrete detail — just express it in words, not as code."

      case "contains_chitchat" =>
        "Your response contains filler or off-topic phrases such as: " +
        "'good luck', 'let me know', 'follow these steps', 'here are some steps', " +
        "'test your function', 'verify that your function returns', " +
        "'make sure to test', 'test boundary cases', or similar encouragements / " +
        "meta-instructions. Remove ALL such phrases. Every sentence must directly " +
        "move the student toward fixing the concrete bug — nothing else."

      case "missing_io_contract_hint" =>
        "The student's issue involves the I/O contract (wrong use of input()/print()/return). " +
        "Your hint MUST explicitly address the relevant I/O concept " +
        "(e.g. 'return' vs 'print', or avoiding 'input()')."

      case "unsupported_structure_hint" =>
        "Your response gives structural advice (e.g. nested loops, dict/set/hash map) " +
        "that is not clearly evidenced in the provided student code. Remove that claim and " +
        "focus only on code-visible facts or runtime/test evidence."

      case r if r.startsWith("too_few_steps(") =>
        val inner = r.stripPrefix("too_few_steps(").stripSuffix(")")
        val parts = inner.split("<")
        val (have, need) =
          if parts.length == 2 then (parts(0).trim, parts(1).trim)
          else ("?", constraints.minSteps.toString)
        s"Your response has only $have numbered step(s) but needs at least $need. " +
        s"Add more concrete, actionable steps until you reach $need."

      case r if r.startsWith("too_many_steps(") =>
        val inner = r.stripPrefix("too_many_steps(").stripSuffix(")")
        val parts = inner.split(">")
        val (have, need) =
          if parts.length == 2 then (parts(0).trim, parts(1).trim)
          else ("?", constraints.maxSteps.toString)
        s"Your response has $have numbered steps but must have at most $need. " +
        s"Merge or remove the least important steps until you have exactly $need or fewer."

      case other =>
        s"Rule violated: '$other'. Review all constraints and fix your response accordingly."
    }

    val ordinal = attemptNr match
      case 1 => "1st"
      case 2 => "2nd"
      case 3 => "3rd"
      case n => s"${n}th"

    val problemList = reasons.zipWithIndex
      .map { case (r, i) => s"  ${i + 1}. ${humanReadable(r)}" }
      .mkString("\n")

    s"""=== REWRITE REQUEST ($ordinal attempt, ${reasons.size} violation(s)) ===

Your previous response was REJECTED because it violated the following rule(s):

$problemList

--- YOUR REJECTED RESPONSE (do NOT repeat these mistakes) ---
$rejectedResponse
--- END OF REJECTED RESPONSE ---

Rewrite your response from scratch, fixing ALL of the violations listed above.
Every rule from the original task below remains fully in force.
Output ONLY the corrected feedback text — no preamble and no explanation of changes.

=== ORIGINAL TASK (unchanged — exercise context, student code, and all constraints) ===

$originalPrompt

=== END OF ORIGINAL TASK ==="""
  }

  def enforce(
    rawText: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String],
    sourceCode: String = ""
  ): GateResult = {
    val trimmed = Option(rawText).getOrElse("").trim

    val preRepairReasons = validateBeforeRepair(trimmed, constraints)

    val repaired = repairBestEffort(trimmed, constraints, requiredTestNames)
    val finalText =
      if repaired.nonEmpty then repaired
      else minimalSafeFallback(constraints, requiredTestNames)

    if preRepairReasons.nonEmpty then
      val postRepairReasons = validate(finalText, constraints, requiredTestNames, sourceCode)
      val allReasons = (preRepairReasons ++ postRepairReasons.filterNot(preRepairReasons.contains)).distinct
      GateResult(passed = false, reasons = allReasons, finalText = finalText)
    else
      val finalReasons = validate(finalText, constraints, requiredTestNames, sourceCode)
      GateResult(passed = finalReasons.isEmpty, reasons = finalReasons, finalText = finalText)
  }

  /**
   * Quick pre-repair check for violations that should trigger an LLM rewrite rather
   * than silent structural stripping. Only checks for things that repairBestEffort /
   * shortenPreservingSteps would destructively remove, leaving surrounding text
   * semantically broken (e.g. an intro sentence ending with ":" whose referenced code
   * was on the next line that gets dropped).
   */
  private def validateBeforeRepair(
    text: String,
    constraints: PromptTemplates.OutputConstraints
  ): Seq[String] = {
    val reasons = scala.collection.mutable.ListBuffer.empty[String]

    if constraints.forbidProvidingFullSolution then {
      val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq
      val hasRawCodeLine = lines.exists { line =>
        val t = line.trim.toLowerCase
        t.startsWith("def ") ||
        t.startsWith("class ") ||
        t.startsWith("import ") ||
        t.startsWith("from ") ||
        t.startsWith("return ")
      }
      if hasRawCodeLine then reasons += "looks_like_solution_code"
    }

    if constraints.forbidBackticks then {
      if text.contains("`") then reasons += "contains_backticks"
    }

    reasons.toList
  }

  /**
   * Attempts to make the text pass constraints by applying safe, rule-based repairs.
   * This is intentionally conservative: we only rewrite structure, never add solution code.
   */
  private def repairBestEffort(
    text: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String]
  ): String = {
    val normalized0 = normalize(text, constraints)

    // If code blocks are forbidden, redact them rather than hard-failing the whole answer.
    // This keeps the response useful while preventing solution leakage.
    val normalized =
      if constraints.forbidCodeBlocks then redactCodeBlocks(normalized0) else normalized0

    val shortened = shortenPreservingSteps(normalized, constraints)
    val ensuredTest =
      if constraints.requireMentionedTestName then ensureMentionedTestName(shortened, requiredTestNames)
      else shortened

    val ensuredSteps = ensureStepCount(ensuredTest, constraints, requiredTestNames)
    ensuredSteps.trim
  }

  private def redactCodeBlocks(text: String): String = {
    if text == null || text.isEmpty then ""
    else {
      // Remove fenced blocks (``` ... ```), preserving surrounding text.
      val fence = "```"
      val sb = new StringBuilder
      var i = 0
      var inFence = false
      while i < text.length do
        if text.startsWith(fence, i) then
          inFence = !inFence
          i += fence.length
        else if inFence then i += 1
        else {
          sb.append(text.charAt(i))
          i += 1
        }
      sb.toString
        .replaceAll("\n{3,}", "\n\n")
        .trim
    }
  }

  private def minimalSafeFallback(
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String]
  ): String = {
    val tn = requiredTestNames.find(_.nonEmpty).getOrElse(if constraints.isGerman then "den fehlschlagenden Fall" else "the failing case")
    val steps =
      if constraints.isGerman then
        Seq(
          s"1. Schau dir an, was dein Code für $tn aktuell zurückgibt, und vergleiche es mit dem erwarteten Ergebnis.",
          "2. Gehe deine Variablen Schritt für Schritt durch, bis du die erste Abweichung findest.",
          "3. Passe nur die Bedingung oder Aktualisierung an, die die Abweichung verursacht."
        ).take(math.max(constraints.minSteps, math.min(constraints.maxSteps, 3)))
      else
        Seq(
          s"1. Re-run $tn and note expected vs observed behavior.",
          "2. Trace your variables step-by-step until the first divergence.",
          "3. Adjust only the condition/update causing that divergence and re-test."
        ).take(math.max(constraints.minSteps, math.min(constraints.maxSteps, 3)))
    steps.mkString("\n").trim
  }

  private def shortenPreservingSteps(text: String, constraints: PromptTemplates.OutputConstraints): String = {
    {
    // Prefer a clean student-facing structure:
    // - one short intro sentence (if present), otherwise insert a neutral intro
    // - 2–4 numbered/bullet steps
    // This removes filler like "Follow these steps:" and drops extra sections.
    val rawLines = text.replace("\r\n", "\n").split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)

    def isStepLine(line: String): Boolean =
      (line.length >= 2 && line.charAt(0).isDigit && line.contains(".")) || line.startsWith("-")

    def normalizeStepLine(line: String, stepNr: Int): String = {
      val t = line.trim
      if t.startsWith("-") then s"$stepNr. " + t.drop(1).trim
      else if t.length >= 2 && t.charAt(0).isDigit && t.contains(".") then t
      else s"$stepNr. $t"
    }

    def isMetaLine(line: String): Boolean = {
      val t = line.trim.toLowerCase
      t == "steps:" ||
      t == "feedback:" ||
      t.startsWith("follow these steps") ||
      t.startsWith("here are") && t.contains("steps") ||
      t.startsWith("to resolve") ||
      t.startsWith("and here's what went wrong") ||
      t.startsWith("here's what went wrong") ||
      t.startsWith("in summary") ||
      t.startsWith("summary")
    }

    def looksGerman(lines: Seq[String]): Boolean = {
      val joined = lines.mkString(" ").toLowerCase
      joined.contains(" du ") || joined.contains(" dein ") || joined.contains(" deine ") ||
      joined.contains(" prüf") || joined.contains(" schau") || joined.contains(" schritte") ||
      joined.contains(" erwart") || joined.contains(" liefert") || joined.contains(" funktioniert")
    }

    def defaultIntro(steps: Seq[String]): String =
      if looksGerman(rawLines) then "So kannst du es eingrenzen:"
      else ""

    // Keep steps; remove pure meta lines from non-step content.
    val stepLines0 = rawLines.filter(isStepLine).take(constraints.maxSteps)
    if stepLines0.isEmpty then text
    else {
      val introCandidates = rawLines.filterNot(isStepLine).filterNot(isMetaLine)
      val intro0raw = introCandidates.headOption.getOrElse(defaultIntro(stepLines0))
      // Strip trailing "Hier sind die Schritte / Follow these steps" suffix that LLMs
      // append to an otherwise useful intro sentence – the numbered steps follow, so
      // the preamble is redundant and would look odd after the normalization.
      val intro0 = intro0raw
        .replaceFirst("(?i)[:\\s]*hier sind (die|einige) schritte[^.]*$", "")
        .replaceFirst("(?i)[:\\s]*folge diesen schritten[^.]*$", "")
        .replaceFirst("(?i)[:\\s]*befolge diese schritte[^.]*$", "")
        .replaceFirst("(?i)[:\\s]*follow these steps[^.]*$", "")
        .replaceFirst("(?i)[:\\s]*here are (a few|some|the) steps[^.]*$", "")
        .trim
        .stripSuffix(":")
        .stripSuffix(".")
        .trim

      // Keep the intro short so truncation does not delete the actual steps.
      val introBudget = math.max(6, math.min(12, constraints.maxWords / 3))
      val intro = truncateToMaxWords(intro0.replace("\n", " ").trim, introBudget)
      val stepLines = stepLines0.zipWithIndex.map { case (l, idx) => normalizeStepLine(l, idx + 1) }
      (if intro.nonEmpty then Seq(intro) ++ stepLines else stepLines).mkString("\n")
    }
    }
  }

  private def ensureMentionedTestName(text: String, requiredTestNames: Seq[String]): String = {
    if requiredTestNames.isEmpty then text
    else {
      val primary = requiredTestNames.find(_.nonEmpty)
      primary match
        case None => text
        case Some(testName) =>
          val alreadyMentions = requiredTestNames.exists(name => name.nonEmpty && text.contains(name))
          if alreadyMentions then text
          else {
            val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq
            val (introLines, rest) = lines.span(l => l.trim.isEmpty)
            val remaining = rest.dropWhile(_.trim.isEmpty)
            val fixed =
              remaining match
                case head +: tail =>
                  val h = head.trim
                  val prefixed = if h.isEmpty then s"$testName" else s"$testName: $h"
                  (introLines :+ prefixed) ++ tail
                case _ => Seq(testName)
            fixed.mkString("\n").trim
          }
    }
  }

  private def ensureStepCount(
    text: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String]
  ): String = {
    val current = countStepLines(text)
    if current >= constraints.minSteps && current <= constraints.maxSteps then text
    else {
      val base =
        if current > constraints.maxSteps then shortenPreservingSteps(text, constraints)
        else text

      val afterShorten = base.replace("\r\n", "\n")
      val missing = math.max(0, constraints.minSteps - countStepLines(afterShorten))
      if missing == 0 then afterShorten
      else {
        val testNameOpt = requiredTestNames.find(_.nonEmpty)
        val existing = countStepLines(afterShorten)
        val genericSteps = (1 to missing).map { i =>
          val stepNr = existing + i
          val tn = testNameOpt.getOrElse(if constraints.isGerman then "den fehlschlagenden Fall" else "the failing case")
          if constraints.isGerman then
            i match
              case 1 => s"$stepNr. Schau dir an, was dein Code für $tn zurückgibt, und vergleiche es mit dem erwarteten Ergebnis."
              case 2 => s"$stepNr. Identifiziere die genaue Bedingung, bei der deine Aktualisierungslogik das Zwischenergebnis ändern sollte."
              case _ => s"$stepNr. Probiere ein kleines Gegenbeispiel und verfolge die Variablenwerte Schritt für Schritt."
          else
            i match
              case 1 => s"$stepNr. Re-run $tn and confirm observed vs expected behavior."
              case 2 => s"$stepNr. Identify the exact condition where your update logic should change the running result."
              case _ => s"$stepNr. Try a tiny counterexample and trace the variable updates."
        }
        (afterShorten.trim + "\n" + genericSteps.mkString("\n")).trim
      }
    }
  }

  private def normalize(text: String, constraints: PromptTemplates.OutputConstraints): String = {
    val normalizedNewlines = text.replace("\r\n", "\n")

    // Remove internal rule identifiers that are not student-friendly, e.g. "PY_RANDOM_USAGE:".
    val withoutInternalRuleKeys =
      normalizedNewlines.replaceAll("\\b(?:PY|VM)_[A-Z0-9_]+\\s*:\\s*", "")

    {
      val withoutBackticks =
        if constraints.forbidBackticks then withoutInternalRuleKeys.replace("`", "") else withoutInternalRuleKeys

      val withoutMarkdownMarkers =
        if constraints.forbidMarkdown then
          withoutBackticks
            .replace("**", "")
            .replace("__", "")
            .replace("###", "")
            .replace("##", "")
        else withoutBackticks

      // Replace em dash and en dash with a plain space so words don't run together.
      val withoutDashes = withoutMarkdownMarkers
        .replace("\u2014", " ") // em dash —
        .replace("\u2013", " ") // en dash –
        .replaceAll(" {2,}", " ")

      // Rewrite "Der Test erwartet …"
      // Tests are an implementation detail; feedback must read as human observation.
      val withoutTestPhrases = withoutDashes
        // German – capital (sentence start); "dass du" is student-directed → "Du sollst"
        .replaceAll("\\bDer [Tt]ests? erwartet,? dass du ", "Du sollst ")
        .replaceAll("\\bDie [Tt]ests? erwarten,? dass du ", "Du sollst ")
        .replaceAll("\\bDer [Tt]ests? erwartet,? dass ", "Deine Funktion soll ")
        .replaceAll("\\bDie [Tt]ests? erwarten,? dass ", "Deine Funktion soll ")
        .replaceAll("\\bDer [Tt]ests? erwartet,? ", "Deine Funktion soll ")
        .replaceAll("\\bDie [Tt]ests? erwarten,? ", "Deine Funktion soll ")
        // German – lowercase (mid sentence)
        .replaceAll("\\bder [Tt]ests? erwartet,? dass du ", "du sollst ")
        .replaceAll("\\bdie [Tt]ests? erwarten,? dass du ", "du sollst ")
        .replaceAll("\\bder [Tt]ests? erwartet,? dass ", "die Funktion soll ")
        .replaceAll("\\bdie [Tt]ests? erwarten,? dass ", "die Funktion soll ")
        .replaceAll("\\bder [Tt]ests? erwartet,? ", "die Funktion soll ")
        .replaceAll("\\bdie [Tt]ests? erwarten,? ", "die Funktion soll ")
        // English
        .replaceAll("(?i)\\bthe tests? expects? that ", "Your function should ")
        .replaceAll("(?i)\\bthe tests? expects?,? ", "Your function should ")
        .replaceAll(" {2,}", " ")

      val lines = withoutTestPhrases.split("\n", -1).toSeq

      val strippedGreeting =
        lines match
          case head +: tail =>
            val h = head.trim
            val h2 = h.replaceFirst("(?i)^(hey|hi|hello)[!,.]?\\s+", "")
            val h3 = h2.replaceFirst("(?i)^(hey|hi|hello|hallo|servus|guten\\s+tag)[!,.]?$", "").trim
            val rebuiltHead = if h3.isEmpty then None else Some(h3)
            (rebuiltHead.toSeq ++ tail)
          case _ => lines

      val cleanedLines =
        if constraints.forbidChitchat then
          strippedGreeting.filterNot { line =>
            val t = line.trim.toLowerCase
            val isStepLine = (t.length >= 2 && t.charAt(0).isDigit && t.contains(".")) || t.startsWith("-")
            // These phrases are banned even inside numbered step lines, because telling
            // the student to manually test/verify/confirm their function is always useless:
            // the system already runs all tests for them.
            val manualTestInstruction =
              t.contains("test your function") ||
                t.contains("test the function") ||
                t.contains("test your code") ||
                t.contains("test the code") ||
                t.contains("verify that your function") ||
                t.contains("verify your function") ||
                t.contains("confirm it behaves") ||
                t.contains("confirm that it") ||
                t.contains("confirm the behavior") ||
                t.contains("test boundary cases") ||
                t.contains("test with a variety") ||
                t.contains("test with different") ||
                t.contains("try different inputs") ||
                t.contains("try various") ||
                t.contains("try your function") ||
                t.contains("make sure to test") ||
                (t.contains("make sure your function") && t.contains("test")) ||
                (t.contains("verify that") && t.contains("expected")) ||
                (t.contains("verify the expected")) ||
                (t.contains("verify the result") && t.contains("input")) ||
                (t.contains("check that your function") && t.contains("expected")) ||
                t.contains("teste deine funktion") ||
                t.contains("teste die funktion") ||
                t.contains("teste deinen code") ||
                t.contains("überprüfe deine funktion") ||
                t.contains("überprüfe die funktion") ||
                t.contains("prüfe deine funktion") ||
                t.contains("bestätige das verhalten") ||
                t.contains("teste mit verschiedenen") ||
                t.contains("teste mit unterschiedlichen") ||
                t.contains("teste mit einer vielzahl") ||
                (t.contains("stelle sicher") && t.contains("funktion") && t.contains("test")) ||
                (t.contains("überprüfe das ergebnis") && t.contains("eingabe"))

            val chitchat =
              t.contains("good luck") ||
                t.contains("let me know") ||
                t.contains("if you need more help") ||
                t.contains("happy to help") ||
                t.contains("you've got this") ||
                t.startsWith("follow these steps") ||
                t.startsWith("here are a few steps") ||
                t.startsWith("here are some steps") ||
                t.contains("dont worry") ||
                t.contains("don't worry") ||
                t.contains("viel erfolg") ||
                t.contains("viel glück") ||
                t.contains("du schaffst das") ||
                t.contains("du kannst das") ||
                t.contains("keine sorge") ||
                t.contains("mach dir keine sorgen") ||
                t.contains("melde dich") ||
                t.contains("sag mir bescheid") ||
                t.contains("lass es mich wissen") ||
                t.contains("wenn du weitere hilfe") ||
                t.contains("bei weiteren fragen") ||
                t.contains("gerne helfe ich") ||
                t.contains("ich helfe gerne") ||
                t.startsWith("folge diesen schritten") ||
                t.startsWith("befolge diese schritte") ||
                t.startsWith("hier sind einige schritte") ||
                t.startsWith("hier sind die schritte") ||
                (t.contains("test") && t.contains("edge case")) ||
                (t.contains("try") && t.contains("edge case")) ||
                (t.contains("check") && t.contains("edge case")) ||
                (t.contains("teste") && t.contains("randfall")) ||
                (t.contains("prüfe") && t.contains("randfall")) ||
                (t.contains("schau") && t.contains("randfall")) ||
                (t.contains("teste") && t.contains("grenzfall")) ||
                (t.contains("prüfe") && t.contains("grenzfall")) ||
                t.contains("run the code to see") ||
                t.contains("run your code to see") ||
                t.contains("run it to see") ||
                t.contains("run the code and") ||
                t.contains("running the code will") ||
                t.contains("try running the code") ||
                (t.contains("run") && t.contains("to see the") && t.contains("error")) ||
                (t.contains("execute") && t.contains("to see") && t.contains("error")) ||
                t.contains("führe den code aus um") ||
                t.contains("starte den code um") ||
                t.contains("probiere den code aus um") ||
                t.contains("führe ihn aus um") ||
                (t.contains("aus") && t.contains("den fehler zu sehen")) ||
                (t.contains("ausführen") && t.contains("zu sehen") && t.contains("fehler"))
            manualTestInstruction || (chitchat && !isStepLine)
          }
        else strippedGreeting

      cleanedLines
        .mkString("\n")
        .replaceAll("\n{3,}", "\n\n")
        .trim
    }
  }

  private def validate(
    text: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String],
    sourceCode: String
  ): Seq[String] = {
    val reasons = scala.collection.mutable.ListBuffer.empty[String]

    if text.isEmpty then reasons += "empty"



    if constraints.requireMentionedTestName then {
      val mentioned = requiredTestNames.exists(name => name.nonEmpty && text.contains(name))
      if requiredTestNames.nonEmpty && !mentioned then reasons += "missing_test_name"
    }

    if constraints.forbidCodeBlocks then {
      if text.contains("```") then reasons += "contains_code_block"
    }

    if constraints.forbidBackticks then {
      if text.contains("`") then reasons += "contains_backticks"
    }

    if constraints.forbidMarkdown then {
      val lower = text.toLowerCase
      val looksLikeMarkdown =
        lower.contains("###") ||
          lower.contains("##") ||
          lower.contains("**") ||
          lower.contains("__")
      if looksLikeMarkdown then reasons += "looks_like_markdown"
    }

    if constraints.forbidChitchat then {
      val lower = text.toLowerCase
      val chitchatMarkers = Seq(
        "good luck",
        "let me know",
        "if you need more help",
        "happy to help",
        "you've got this",
        "hey!",
        "hi!",
        "hello",
        "follow these steps",
        "here are a few steps",
        "here are some steps",
        "thanks for",
        "don't worry",
        "run the code to see",
        "run your code to see",
        "run it to see",
        "try running the code",
        "running the code will",
        "the test expects",
        "the tests expect",
        "viel erfolg",
        "viel glück",
        "du schaffst das",
        "du kannst das",
        "keine sorge",
        "mach dir keine sorgen",
        "melde dich",
        "sag mir bescheid",
        "lass es mich wissen",
        "wenn du weitere hilfe",
        "bei weiteren fragen",
        "gerne helfe ich",
        "ich helfe gerne",
        "folge diesen schritten",
        "befolge diese schritte",
        "hier sind einige schritte",
        "hier sind die schritte",
        "hallo!",
        "danke für",
        "führe den code aus um",
        "starte den code um",
        "probiere den code aus um",
        "der test erwartet",
        "die tests erwarten",
        "test your function",
        "test the function",
        "verify that your function",
        "verify your function",
        "confirm it behaves",
        "make sure to test",
        "test boundary cases",
        "test with a variety",
        "test with different",
        "teste deine funktion",
        "teste die funktion",
        "überprüfe deine funktion",
        "prüfe deine funktion",
        "teste mit verschiedenen"
      )
      if chitchatMarkers.exists(lower.contains) then reasons += "contains_chitchat"
    }

    val textLower = text.toLowerCase
    val sourceLower = Option(sourceCode).getOrElse("").toLowerCase

    def sourceMentionsAny(markers: Seq[String]): Boolean =
      markers.exists(m => sourceLower.contains(m))

    def hasNestedLoopsInSource(code: String): Boolean = {
      val lines = Option(code).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
      val loopIndents = lines.flatMap { line =>
        val indent = line.takeWhile(_ == ' ').length
        val trimmed = line.dropWhile(_ == ' ')
        if trimmed.startsWith("for ") || trimmed.startsWith("while ") then Some(indent) else None
      }
      loopIndents.size >= 2 && loopIndents.max > loopIndents.min
    }

    val mentionsDictSetAdvice =
      textLower.contains("dictionary") || textLower.contains("dict") ||
        textLower.contains("set") || textLower.contains("hashmap")
    val sourceHasDictSet =
      sourceMentionsAny(Seq("dict(", "set(", "{}", "{", "}", "defaultdict", "counter("))

    if mentionsDictSetAdvice && !sourceHasDictSet then
      reasons += "unsupported_structure_hint"

    val mentionsNestedLoopAdvice =
      textLower.contains("nested loop") ||
        textLower.contains("nested loops") ||
        textLower.contains("verschachtelte schleife") ||
        textLower.contains("verschachtelte schleifen")

    if mentionsNestedLoopAdvice && !hasNestedLoopsInSource(sourceCode) then
      reasons += "unsupported_structure_hint"

    if constraints.forbidProvidingFullSolution then {
      val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq
      val looksLikeCodeLine = lines.exists { line =>
        val t = line.trim.toLowerCase
        t.startsWith("def ") ||
        t.startsWith("class ") ||
        t.startsWith("import ") ||
        t.startsWith("from ") ||
        t.startsWith("return ")
      }
      if looksLikeCodeLine then reasons += "looks_like_solution_code"
    }

    // Minimal issue-specific checks (kept intentionally conservative)
    constraints.issueTypeHint.foreach {
      case DecisionLayer.IssueType.IO_CONTRACT =>
        val lower = text.toLowerCase
        val mentionsIo = lower.contains("input") || lower.contains("print") || lower.contains("ausgabe") || lower.contains("eingabe")
        if !mentionsIo then reasons += "missing_io_contract_hint"
      case _ =>
    }

    val steps = countStepLines(text)
    if steps < constraints.minSteps then reasons += s"too_few_steps($steps<${constraints.minSteps})"
    if steps > constraints.maxSteps then reasons += s"too_many_steps($steps>${constraints.maxSteps})"

    reasons.toList
  }

  private def wordCount(text: String): Int =
    text.split("\\s+").count(_.nonEmpty)

  private def truncateToMaxWords(text: String, maxWords: Int): String = text

  private def countStepLines(text: String): Int = {
    val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)
    lines.count { line =>
      (line.length >= 2 && line.charAt(0).isDigit && line.contains(".")) || line.startsWith("-")
    }
  }
}
