package it.evadid.workbook.elements.interactionElements.programming.state.snap

import scala.util.control.NonFatal
import SnapScalaModel.*

/** Cross-platform lexical reader. Does not use a DOM, resolve references, or fetch DTDs. */
private[snap] object SnapScalaXmlCodec {
  def parse(xml: String): Either[String, Document] = {
    try {
      require(xml != null && xml.length <= 64 * 1024 * 1024, "Snap XML exceeds the 64 MiB input limit.")
      Right(Reader(xml).document())
    } catch {
      case NonFatal(error) => Left(Option(error.getMessage).getOrElse("Invalid Snap XML."))
    }
  }

  private final class Reader(xml: String) {
    private var cursor = 0
    private var count = 0
    private def starts(value: String): Boolean = xml.startsWith(value, cursor)
    private def check(ok: Boolean, message: String): Unit =
      require(ok, s"$message (offset $cursor)")
    private def space(): String = {
      val start = cursor
      while cursor < xml.length && xml.charAt(cursor).isWhitespace do cursor += 1
      xml.substring(start, cursor)
    }
    private def name(): String = {
      val start = cursor
      check(cursor < xml.length && (xml.charAt(cursor).isLetter || "_:".contains(xml.charAt(cursor))), "Expected XML name")
      cursor += 1
      while cursor < xml.length && (xml.charAt(cursor).isLetterOrDigit || "_:-.".contains(xml.charAt(cursor))) do cursor += 1
      xml.substring(start, cursor)
    }
    private def delimited(open: String, close: String): String = {
      cursor += open.length
      val start = cursor
      val end = xml.indexOf(close, cursor)
      check(end >= 0, s"Unterminated $open")
      cursor = end + close.length
      xml.substring(start, end)
    }
    def document(): Document = {
      val nodes = Vector.newBuilder[Node]
      while cursor < xml.length do nodes += node(0)
      Document(nodes.result())
    }
    private def node(depth: Int): Node = {
      count += 1
      check(count <= 1000000, "Too many XML nodes")
      check(depth <= 256, "Snap XML nesting exceeds 256 levels")
      if starts("<!--") then XmlComment(delimited("<!--", "-->"))
      else if starts("<![CDATA[") then CData(delimited("<![CDATA[", "]]>"))
      else if starts("<?") then ProcessingInstruction(delimited("<?", "?>"))
      else if starts("<!DOCTYPE") then declaration()
      else if starts("<") then element(depth)
      else {
        val start = cursor
        val end = xml.indexOf('<', cursor)
        cursor = if end < 0 then xml.length else end
        val raw = xml.substring(start, cursor)
        decode(raw) // validate numeric entities without normalizing their spelling
        Text(raw)
      }
    }
    private def declaration(): Declaration = {
      val start = cursor
      cursor += "<!DOCTYPE".length
      var brackets = 0
      var quote = ' '
      var done = false
      while cursor < xml.length && !done do {
        val ch = xml.charAt(cursor)
        if quote != ' ' then { if ch == quote then quote = ' ' }
        else if ch == '\'' || ch == '"' then quote = ch
        else if ch == '[' then brackets += 1
        else if ch == ']' then brackets -= 1
        else if ch == '>' && brackets == 0 then done = true
        cursor += 1
      }
      check(done, "Unterminated DOCTYPE")
      Declaration(xml.substring(start, cursor))
    }
    private def element(depth: Int): SnapElement = {
      cursor += 1
      val tag = name()
      val attrs = Vector.newBuilder[Attribute]
      var names = Set.empty[String]
      var trailing = ""
      var headerDone = false
      while !headerDone do {
        val leading = space()
        check(cursor < xml.length, "Unterminated element header")
        if starts(">") || starts("/>") then { trailing = leading; headerDone = true }
        else {
          check(leading.nonEmpty, "Expected whitespace before attribute")
          val attrName = name()
          check(!names.contains(attrName), s"Duplicate attribute $attrName")
          names += attrName
          val before = space()
          check(starts("="), "Expected attribute equals sign")
          cursor += 1
          val after = space()
          check(cursor < xml.length && (xml.charAt(cursor) == '\'' || xml.charAt(cursor) == '"'), "Expected quoted attribute")
          val quote = xml.charAt(cursor)
          cursor += 1
          val start = cursor
          val end = xml.indexOf(quote, cursor)
          check(end >= 0, "Unterminated attribute")
          val raw = xml.substring(start, end)
          check(!raw.contains('<'), "Unescaped less-than sign in attribute")
          decode(raw)
          cursor = end + 1
          attrs += Attribute(attrName, raw, leading, before, after, quote.toString)
        }
      }
      val selfClosing = starts("/>")
      cursor += (if selfClosing then 2 else 1)
      if selfClosing then classify(Element(tag, attrs.result(), trailing = trailing, selfClosing = true))
      else {
        val children = Vector.newBuilder[Node]
        while cursor < xml.length && !starts("</") do children += node(depth + 1)
        check(starts("</"), s"Missing closing tag for $tag")
        val closeStart = cursor
        cursor += 2
        val closeName = name()
        check(closeName == tag, s"Mismatched closing tag $closeName, expected $tag")
        space()
        check(starts(">"), "Unterminated closing tag")
        cursor += 1
        classify(Element(tag, attrs.result(), children.result(), trailing, false, xml.substring(closeStart, cursor)))
      }
    }
  }
}
