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
    val repaired = repair(trimmed, constraints, requiredTestNames)
    val reasons = validate(repaired, constraints, requiredTestNames)
    GateResult(passed = reasons.isEmpty, reasons = reasons, finalText = repaired)
  }

  /**
   * Attempts to make the text pass constraints by applying safe, rule-based repairs.
   * This is intentionally conservative: we only rewrite structure, never add solution code.
   */
  private def repair(
    text: String,
    constraints: PromptTemplates.OutputConstraints,
    requiredTestNames: Seq[String]
  ): String = {
    val normalized = normalize(text, constraints)
    val withoutCodeBlocks =
      if constraints.forbidCodeBlocks then stripCodeBlocks(normalized) else normalized

    val shortened = shortenPreservingSteps(withoutCodeBlocks, constraints)
    val ensuredTest =
      if constraints.requireMentionedTestName then ensureMentionedTestName(shortened, requiredTestNames)
      else shortened

    val ensuredSteps = ensureStepCount(ensuredTest, constraints, requiredTestNames)
    val truncated = truncateToMaxWords(ensuredSteps.trim, constraints.maxWords)
    truncated.trim
  }

  private def shortenPreservingSteps(text: String, constraints: PromptTemplates.OutputConstraints): String = {
    // Prefer a clean student-facing structure:
    // - one short intro sentence (if present), otherwise insert a neutral intro
    // - 2–4 numbered/bullet steps
    // This removes filler like "Follow these steps:" and drops extra sections.
    val rawLines = text.replace("\r\n", "\n").split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)

    def isStepLine(line: String): Boolean =
      (line.length >= 2 && line.charAt(0).isDigit && line.contains(".")) || line.startsWith("-")

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
    val stepLines = rawLines.filter(isStepLine).take(constraints.maxSteps)
    if stepLines.isEmpty then text
    else {
      val introCandidates = rawLines.filterNot(isStepLine).filterNot(isMetaLine)
      val intro = introCandidates.headOption.getOrElse(defaultIntro(stepLines))
      (Seq(intro) ++ stepLines).mkString("\n")
    }
  }

  private def stripCodeBlocks(text: String): String = {
    val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq
    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    var inBlock = false
    lines.foreach { line =>
      val trimmed = line.trim
      if trimmed.startsWith("```") then inBlock = !inBlock
      else if !inBlock then out += line
    }
    out.mkString("\n").trim
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
        val genericSteps = (1 to missing).map { i =>
          val tn = testNameOpt.getOrElse("the failing case")
          i match
            case 1 => s"- Re-run $tn and confirm the observed vs expected behavior."
            case 2 => s"- Identify the exact condition where your update logic should change the running result."
            case _ => s"- Try a tiny counterexample that should change the outcome and trace the variable updates."
        }
        (afterShorten.trim + "\n" + genericSteps.mkString("\n")).trim
      }
    }
  }

  private def normalize(text: String, constraints: PromptTemplates.OutputConstraints): String = {
    val normalizedNewlines = text.replace("\r\n", "\n")

    // Never try to "clean" real code blocks; those should fail the gate.
    if constraints.forbidCodeBlocks && normalizedNewlines.contains("```") then normalizedNewlines
    else {
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
      val words = text.split("\\s+").filter(_.nonEmpty)
      if words.length <= maxWords then text
      else words.take(maxWords).mkString(" ")
    }
  }

  private def countStepLines(text: String): Int = {
    val lines = text.replace("\r\n", "\n").split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)
    lines.count { line =>
      (line.length >= 2 && line.charAt(0).isDigit && line.contains(".")) || line.startsWith("-")
    }
  }
}
