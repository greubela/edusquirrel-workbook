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

  def enforce(
    rawText: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String]
  ): GateResult = {
    val trimmed = Option(rawText).getOrElse("").trim
    val repaired = repairBestEffort(trimmed, constraints, requiredTestNames)
    val reasons = validate(repaired, constraints, requiredTestNames)
    val finalText =
      if repaired.nonEmpty then repaired
      else minimalSafeFallback(constraints, requiredTestNames)
    val finalReasons = validate(finalText, constraints, requiredTestNames)
    GateResult(passed = finalReasons.isEmpty, reasons = finalReasons, finalText = finalText)
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
    val truncated = truncateToMaxWords(ensuredSteps.trim, constraints.maxWords)
    truncated.trim
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
    val tn = requiredTestNames.find(_.nonEmpty).getOrElse("the failing case")
    val intro = "Try this:"
    val steps =
      Seq(
        s"1. Re-run $tn and note expected vs observed behavior.",
        "2. Trace your variables step-by-step until the first divergence.",
        "3. Adjust only the condition/update causing that divergence and re-test."
      ).take(math.max(constraints.minSteps, math.min(constraints.maxSteps, 3)))
    truncateToMaxWords((intro + "\n" + steps.mkString("\n")).trim, constraints.maxWords)
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
      else "Try this:"

    // Keep steps; remove pure meta lines from non-step content.
    val stepLines0 = rawLines.filter(isStepLine).take(constraints.maxSteps)
    if stepLines0.isEmpty then text
    else {
      val introCandidates = rawLines.filterNot(isStepLine).filterNot(isMetaLine)
      val intro0 = introCandidates.headOption.getOrElse(defaultIntro(stepLines0))

      // Keep the intro short so truncation does not delete the actual steps.
      val introBudget = math.max(6, math.min(12, constraints.maxWords / 3))
      val intro = truncateToMaxWords(intro0.replace("\n", " ").trim, introBudget)
      val stepLines = stepLines0.zipWithIndex.map { case (l, idx) => normalizeStepLine(l, idx + 1) }
      (Seq(intro) ++ stepLines).mkString("\n")
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
          val tn = testNameOpt.getOrElse("the failing case")
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

    {
      val withoutBackticks =
        if constraints.forbidBackticks then normalizedNewlines.replace("`", "") else normalizedNewlines

      val withoutMarkdownMarkers =
        if constraints.forbidMarkdown then
          withoutBackticks
            .replace("**", "")
            .replace("__", "")
            .replace("###", "")
            .replace("##", "")
        else withoutBackticks

      val lines = withoutMarkdownMarkers.split("\n", -1).toSeq

      val strippedGreeting =
        lines match
          case head +: tail =>
            val h = head.trim
            val h2 = h.replaceFirst("(?i)^(hey|hi|hello)[!,.]?\\s+", "")
            val h3 = h2.replaceFirst("(?i)^(hey|hi|hello)[!,.]?$", "").trim
            val rebuiltHead = if h3.isEmpty then None else Some(h3)
            (rebuiltHead.toSeq ++ tail)
          case _ => lines

      val cleanedLines =
        if constraints.forbidChitchat then
          strippedGreeting.filterNot { line =>
            val t = line.trim.toLowerCase
            val isStepLine = (t.length >= 2 && t.charAt(0).isDigit && t.contains(".")) || t.startsWith("-")
            val chitchat =
              t.contains("good luck") ||
                t.contains("let me know") ||
                t.contains("if you need more help") ||
                t.contains("happy to help") ||
                t.contains("you've got this") ||
                t.contains("follow these steps") ||
                t.contains("here are a few steps") ||
                t.contains("here are some steps") ||
                t.contains("dont worry") ||
                t.contains("don't worry")
            chitchat && !isStepLine
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
    requiredTestNames: Seq[String]
  ): Seq[String] = {
    val reasons = scala.collection.mutable.ListBuffer.empty[String]

    if text.isEmpty then reasons += "empty"

    val words = wordCount(text)
    if words > constraints.maxWords then reasons += s"too_long($words>${constraints.maxWords})"

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
        "don't worry"
      )
      if chitchatMarkers.exists(lower.contains) then reasons += "contains_chitchat"
    }

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

  private def truncateToMaxWords(text: String, maxWords: Int): String = {
    if maxWords <= 0 then ""
    else {
      // Important: preserve newlines and step formatting.
      // Joining words with spaces would collapse numbered/bulleted lists into one line,
      // which then fails the step-count validation.
      val Word = "\\S+".r
      val matches = Word.findAllMatchIn(text).toIndexedSeq
      if matches.length <= maxWords then text
      else {
        val cut = matches(maxWords - 1).end
        text.substring(0, cut).trim
      }
    }
  }

  private def countStepLines(text: String): Int = {
    val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)
    lines.count { line =>
      (line.length >= 2 && line.charAt(0).isDigit && line.contains(".")) || line.startsWith("-")
    }
  }
}
