package it.evadid.core.util

import scala.annotation.tailrec
import scala.util.matching.Regex

final class MarkdownToHtml {

  private val specialChars: Set[Char] = Set('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!')

  def transform(markdown: String): String = {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    @tailrec def render(index: Int, blocks: List[String]): String =
      if index >= lines.size then blocks.reverse.mkString
      else {
        val (next, html) = parseBlock(lines, index)
        render(next, html :: blocks)
      }
    render(0, Nil)
  }

  private def parseBlock(lines: IndexedSeq[String], index: Int): (Int, String) = {
    val trimmed = lines(index).trim
    if trimmed.isEmpty then (index + 1, "")
    else if trimmed.startsWith("```") then parseFencedCodeBlock(lines, index)
    else if isHorizontalRule(trimmed) then (index + 1, "<hr />")
    else trimmed match {
      case headingRegex(hashes, content) =>
        val level = hashes.length
        (index + 1, s"<h$level>${parseInline(content.trim)}</h$level>")
      case _ if trimmed.startsWith(">") => parseBlockQuote(lines, index)
      case _ if isListLine(trimmed) => parseList(lines, index)
      case _ => parseParagraph(lines, index)
    }
  }

  private def parseFencedCodeBlock(lines: IndexedSeq[String], startIndex: Int): (Int, String) = {
    val info = lines(startIndex).trim.drop(3).trim
    val languageClass = if info.nonEmpty then s" class=\"language-${escapeHtml(info)}\"" else ""
    val closing = lines.indexWhere(_.trim.startsWith("```"), startIndex + 1)
    val end = if closing < 0 then lines.size else closing
    val codeLines = lines.slice(startIndex + 1, end)
    val trailingNewline = if closing >= 0 && codeLines.nonEmpty then "\n" else ""
    val code = codeLines.mkString("\n") + trailingNewline
    (if closing < 0 then end else end + 1, s"<pre><code$languageClass>${escapeHtml(code)}</code></pre>")
  }

  private def parseBlockQuote(lines: IndexedSeq[String], startIndex: Int): (Int, String) = {
    val quotes = lines.view.drop(startIndex).takeWhile(_.trim.startsWith(">"))
      .map(_.trim.stripPrefix(">").stripPrefix(" ")).toVector
    (startIndex + quotes.size, s"<blockquote>${transform(quotes.mkString("\n"))}</blockquote>")
  }

  private def parseList(lines: IndexedSeq[String], startIndex: Int): (Int, String) = {
    val ordered = orderedListRegex.pattern.matcher(lines(startIndex).trim).matches()
    val itemRegex = if ordered then orderedListRegex else unorderedListRegex
    val tag = if ordered then "ol" else "ul"
    @tailrec def collect(index: Int, items: Vector[String]): (Int, Vector[String]) =
      if index >= lines.size then (index, items)
      else {
        val trimmed = lines(index).trim
        itemRegex.findFirstMatchIn(trimmed) match {
          case Some(item) => collect(index + 1, items :+ parseInline(item.group(1).trim))
          case None if trimmed.nonEmpty && lines(index).startsWith("  ") && items.nonEmpty =>
            collect(index + 1, items.updated(items.size - 1, items.last + " " + parseInline(trimmed)))
          case None => (index, items)
        }
      }
    val (next, items) = collect(startIndex, Vector.empty)
    (next, items.map(item => s"<li>$item</li>").mkString(s"<$tag>", "", s"</$tag>"))
  }

  private def parseParagraph(lines: IndexedSeq[String], startIndex: Int): (Int, String) = {
    val paragraph = lines.view.drop(startIndex).takeWhile(line => !isParagraphStop(line)).toVector
    val body = paragraph.zipWithIndex.map { (rawLine, index) =>
      val explicitBreak = rawLine.endsWith("  ") || rawLine.endsWith("\\")
      val inlineText = parseInline(rawLine.trim.stripSuffix("\\").stripSuffix("  "))
      val separator = if index == paragraph.size - 1 then "" else if explicitBreak then "<br />" else " "
      inlineText + separator
    }.mkString
    (startIndex + paragraph.size, s"<p>$body</p>")
  }

  private def isParagraphStop(line: String): Boolean = {
    val trimmed = line.trim
    trimmed.isEmpty || trimmed.startsWith("```") || isHorizontalRule(trimmed) ||
      isHeading(trimmed) || trimmed.startsWith(">") || isListLine(trimmed)
  }

  private def parseInline(text: String): String = {
    // Protect literal punctuation, code and generated tags from subsequent markup passes.
    // Choose a prefix absent from the input so user text can never become a placeholder.
    val prefix = Iterator.iterate("@@MDTOKEN")(_ + "X").dropWhile(text.contains).next()
    val (source, literalTokens) = protectInlineLiterals(text, prefix)
    val (withImages, imageTokens) = protectMatches(source, imageRegex, prefix, literalTokens) { image =>
      val alt = escapeHtml(image.group(1))
      val src = escapeHtml(image.group(2))
      val titleAttr = Option(image.group(4)).map(v => s" title=\"${escapeHtml(v)}\"").getOrElse("")
      s"<img src=\"$src\" alt=\"$alt\"$titleAttr />"
    }
    val (withLinks, tokens) = protectMatches(withImages, linkRegex, prefix, imageTokens) { link =>
      val label = parseInline(link.group(1))
      val href = escapeHtml(link.group(2))
      val titleAttr = Option(link.group(4)).map(v => s" title=\"${escapeHtml(v)}\"").getOrElse("")
      s"<a href=\"$href\"$titleAttr>$label</a>"
    }

    val withEm = renderEmphasis(escapeHtml(withLinks))

    // Tags may contain earlier placeholders, for example escaped punctuation in link labels.
    tokens.indices.reverse.foldLeft(withEm) { (acc, i) =>
      acc.replace(s"$prefix$i@@", tokens(i))
    }
  }

  private def protectInlineLiterals(text: String, prefix: String): (String, Vector[String]) = {
    @tailrec def collect(index: Int, parts: List[String], tokens: Vector[String]): (String, Vector[String]) =
      if index >= text.length then (parts.reverse.mkString, tokens)
      else {
        val ch = text.charAt(index)
        val closing = if ch == '`' then text.indexOf('`', index + 1) else -1
        val (next, protectedHtml) =
          if ch == '\\' && index + 1 < text.length && specialChars.contains(text.charAt(index + 1)) then
            (index + 2, Some(escapeHtml(text.charAt(index + 1).toString)))
          else if closing >= 0 then
            (closing + 1, Some(s"<code>${escapeHtml(text.substring(index + 1, closing))}</code>"))
          else (index + 1, None)
        protectedHtml match {
          case Some(html) => collect(next, s"$prefix${tokens.size}@@" :: parts, tokens :+ html)
          case None => collect(next, text.substring(index, next) :: parts, tokens)
        }
      }
    collect(0, Nil, Vector.empty)
  }

  private def protectMatches(source: String, pattern: Regex, prefix: String, initialTokens: Vector[String])
                            (render: Regex.Match => String): (String, Vector[String]) = {
    val (end, parts, tokens) = pattern.findAllMatchIn(source).foldLeft((0, List.empty[String], initialTokens)) {
      case ((previousEnd, parts, tokens), matched) =>
        val token = s"$prefix${tokens.size}@@"
        (matched.end, token :: source.substring(previousEnd, matched.start) :: parts, tokens :+ render(matched))
    }
    (parts.reverse.mkString + source.substring(end), tokens)
  }

  private def renderEmphasis(source: String): String = {
    def literalReplacement(value: String): String = java.util.regex.Matcher.quoteReplacement(value)
    val withStrongEmphasis = strongEmRegex.replaceAllIn(source, m => literalReplacement(s"<strong><em>${m.group(2)}</em></strong>"))
    val withStrong = strongRegex.replaceAllIn(withStrongEmphasis, m => literalReplacement(s"<strong>${m.group(2)}</strong>"))
    val withStarEm = emAsteriskRegex.replaceAllIn(withStrong, m => literalReplacement(s"<em>${m.group(1)}</em>"))
    val withEm = emUnderscoreRegex.replaceAllIn(withStarEm, m => literalReplacement(s"<em>${m.group(1)}</em>"))

    withEm
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

  private val headingRegex: Regex = "^(#{1,6})\\s+(.+)$".r
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
