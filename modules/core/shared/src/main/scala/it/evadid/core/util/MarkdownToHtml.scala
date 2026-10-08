package it.evadid.core.util

import scala.collection.mutable
import scala.util.matching.Regex

final class MarkdownToHtml {

  private val specialChars: Set[Char] = Set('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!')

  def transform(markdown: String): String = {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toList
    val html = new StringBuilder

    var index = 0
    while (index < lines.length) {
      val line = lines(index)
      val trimmed = line.trim

      if (trimmed.isEmpty) {
        index += 1
      } else if (trimmed.startsWith("```")) {
        val (nextIndex, codeHtml) = parseFencedCodeBlock(lines, index)
        html.append(codeHtml)
        index = nextIndex
      } else if (isHorizontalRule(trimmed)) {
        html.append("<hr />")
        index += 1
      } else if (isHeading(trimmed)) {
        val headingMatch = "^(#{1,6})\\s+(.+)$".r.findFirstMatchIn(trimmed).get
        val level = headingMatch.group(1).length
        html.append(s"<h$level>${parseInline(headingMatch.group(2).trim)}</h$level>")
        index += 1
      } else if (trimmed.startsWith(">")) {
        val (nextIndex, quoteHtml) = parseBlockQuote(lines, index)
        html.append(quoteHtml)
        index = nextIndex
      } else if (isListLine(trimmed)) {
        val (nextIndex, listHtml) = parseList(lines, index)
        html.append(listHtml)
        index = nextIndex
      } else {
        val (nextIndex, paragraphHtml) = parseParagraph(lines, index)
        html.append(paragraphHtml)
        index = nextIndex
      }
    }

    html.toString()
  }

  private def parseFencedCodeBlock(lines: List[String], startIndex: Int): (Int, String) = {
    val opening = lines(startIndex).trim
    val info = opening.drop(3).trim
    val languageClass = if (info.nonEmpty) s" class=\"language-${escapeHtml(info)}\"" else ""

    val codeLines = new StringBuilder
    var idx = startIndex + 1
    var closed = false

    while (idx < lines.length && !closed) {
      val cur = lines(idx)
      if (cur.trim.startsWith("```")) {
        closed = true
        idx += 1
      } else {
        codeLines.append(cur)
        if (idx < lines.length - 1) codeLines.append("\n")
        idx += 1
      }
    }

    (idx, s"<pre><code$languageClass>${escapeHtml(codeLines.toString())}</code></pre>")
  }

  private def parseBlockQuote(lines: List[String], startIndex: Int): (Int, String) = {
    val quoteLines = mutable.ListBuffer.empty[String]
    var idx = startIndex

    while (idx < lines.length && lines(idx).trim.startsWith(">")) {
      val stripped = lines(idx).trim.stripPrefix(">")
      quoteLines += stripped.stripPrefix(" ")
      idx += 1
    }

    (idx, s"<blockquote>${transform(quoteLines.mkString("\n"))}</blockquote>")
  }

  private def parseList(lines: List[String], startIndex: Int): (Int, String) = {
    val ordered = orderedListRegex.pattern.matcher(lines(startIndex).trim).matches()
    val itemRegex = if (ordered) orderedListRegex else unorderedListRegex
    val tag = if (ordered) "ol" else "ul"

    val items = mutable.ListBuffer.empty[String]
    var idx = startIndex

    while (idx < lines.length) {
      val trimmed = lines(idx).trim
      if (trimmed.isEmpty || !itemRegex.pattern.matcher(trimmed).matches()) {
        if (trimmed.nonEmpty && lines(idx).startsWith("  ") && items.nonEmpty) {
          val previous = items.remove(items.length - 1)
          items += (previous + " " + parseInline(trimmed))
          idx += 1
        } else {
          return (idx, s"<$tag>${items.map(i => s"<li>$i</li>").mkString}</$tag>")
        }
      } else {
        val itemContent = itemRegex.findFirstMatchIn(trimmed).map(_.group(1)).getOrElse("")
        items += parseInline(itemContent.trim)
        idx += 1
      }
    }

    (idx, s"<$tag>${items.map(i => s"<li>$i</li>").mkString}</$tag>")
  }

  private def parseParagraph(lines: List[String], startIndex: Int): (Int, String) = {
    val paragraphLines = mutable.ListBuffer.empty[String]
    var idx = startIndex

    while (idx < lines.length && !isParagraphStop(lines, idx)) {
      paragraphLines += lines(idx)
      idx += 1
    }

    val body = paragraphLines.zipWithIndex.map { case (rawLine, lineIndex) =>
      val trimmedLine = rawLine.trim
      val explicitBreak = rawLine.endsWith("  ") || rawLine.endsWith("\\")
      val inlineText = parseInline(trimmedLine.stripSuffix("\\").stripSuffix("  "))
      val separator = if (lineIndex < paragraphLines.length - 1) {
        if (explicitBreak) "<br />" else " "
      } else ""
      inlineText + separator
    }.mkString

    (idx, s"<p>$body</p>")
  }

  private def isParagraphStop(lines: List[String], idx: Int): Boolean = {
    val trimmed = lines(idx).trim
    trimmed.isEmpty ||
      trimmed.startsWith("```") ||
      isHorizontalRule(trimmed) ||
      isHeading(trimmed) ||
      trimmed.startsWith(">") ||
      isListLine(trimmed)
  }

  private def parseInline(text: String): String = {
    // Protect literal punctuation, code and generated tags from subsequent markup passes.
    // Choose a prefix absent from the input so user text can never become a placeholder.
    var prefix = "@@MDTOKEN"
    while (text.contains(prefix)) prefix += "X"
    val protectedHtml = mutable.ArrayBuffer.empty[String]
    def protect(html: String): String = {
      val token = s"$prefix${protectedHtml.length}@@"
      protectedHtml += html
      token
    }

    val source = new StringBuilder
    var index = 0
    while (index < text.length) {
      val ch = text.charAt(index)
      if (ch == '\\' && index + 1 < text.length && specialChars.contains(text.charAt(index + 1))) {
        source.append(protect(escapeHtml(text.charAt(index + 1).toString)))
        index += 2
      } else if (ch == '`' && text.indexOf('`', index + 1) >= 0) {
        val end = text.indexOf('`', index + 1)
        source.append(protect(s"<code>${escapeHtml(text.substring(index + 1, end))}</code>"))
        index = end + 1
      } else {
        source.append(ch)
        index += 1
      }
    }

    val withImages = imageRegex.replaceAllIn(source.toString(), m => {
      val alt = escapeHtml(m.group(1))
      val src = escapeHtml(m.group(2))
      val titleAttr = Option(m.group(4)).map(v => s" title=\"${escapeHtml(v)}\"").getOrElse("")
      protect(s"<img src=\"$src\" alt=\"$alt\"$titleAttr />")
    })

    val withLinks = linkRegex.replaceAllIn(withImages, m => {
      val label = parseInline(m.group(1))
      val href = escapeHtml(m.group(2))
      val titleAttr = Option(m.group(4)).map(v => s" title=\"${escapeHtml(v)}\"").getOrElse("")
      protect(s"<a href=\"$href\"$titleAttr>$label</a>")
    })

    def literalReplacement(value: String): String = java.util.regex.Matcher.quoteReplacement(value)
    val escapedSource = escapeHtml(withLinks)
    val withStrongEmphasis = strongEmRegex.replaceAllIn(escapedSource, m => literalReplacement(s"<strong><em>${m.group(2)}</em></strong>"))
    val withStrong = strongRegex.replaceAllIn(withStrongEmphasis, m => literalReplacement(s"<strong>${m.group(2)}</strong>"))
    val withStarEm = emAsteriskRegex.replaceAllIn(withStrong, m => literalReplacement(s"<em>${m.group(1)}</em>"))
    val withEm = emUnderscoreRegex.replaceAllIn(withStarEm, m => literalReplacement(s"<em>${m.group(1)}</em>"))

    // Tags may contain earlier placeholders, for example escaped punctuation in link labels.
    protectedHtml.indices.reverse.foldLeft(withEm) { (acc, i) =>
      acc.replace(s"$prefix$i@@", protectedHtml(i))
    }
  }

  private def escapeHtml(raw: String): String =
    raw
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  private def isHeading(trimmed: String): Boolean = headingRegex.pattern.matcher(trimmed).matches()

  private def isListLine(trimmed: String): Boolean =
    unorderedListRegex.pattern.matcher(trimmed).matches() || orderedListRegex.pattern.matcher(trimmed).matches()

  private def isHorizontalRule(trimmed: String): Boolean =
    trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|(-\\s-\\s-)|(_\\s_\\s_)|(\\*\\s\\*\\s\\*))$")

  private val headingRegex: Regex = "^(#{1,6})\\s+.+$".r
  private val unorderedListRegex: Regex = "^[-*+]\\s+(.+)$".r
  private val orderedListRegex: Regex = "^\\d+\\.\\s+(.+)$".r

  private val imageRegex: Regex = "!\\[([^\\]]*)\\]\\(([^\\s\\)]+)(\\s+\"([^\"]+)\")?\\)".r
  private val linkRegex: Regex = "\\[([^\\]]+)\\]\\(([^\\s\\)]+)(\\s+\"([^\"]+)\")?\\)".r
  private val strongEmRegex: Regex = "(\\*\\*\\*|___)(.+?)\\1".r
  private val strongRegex: Regex = "(\\*\\*|__)(.+?)\\1".r
  private val emAsteriskRegex: Regex = "\\*([^*]+)\\*".r
  private val emUnderscoreRegex: Regex = "_([^_]+)_".r
}

object MarkdownToHtml {
  private val parser = new MarkdownToHtml

  def transform(markdown: String): String = parser.transform(markdown)
}
