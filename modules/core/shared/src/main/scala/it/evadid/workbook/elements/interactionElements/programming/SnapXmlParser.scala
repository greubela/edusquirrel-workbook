package it.evadid.workbook.elements.interactionElements.programming

/**
 * Parser for Snap project XML: find tags, attributes and children.
 *
 * Snap XML is produced by Snap's own serializer and by [[SnapProjectXml]], so it
 * always uses double-quoted attributes, no CDATA and no namespaces. That is narrow
 * enough for a string scanner and keeps shared code free of an XML dependency.
 */
object SnapXmlParser {

  /** One parsed element; `outer` is the exact source slice, so it can be spliced back verbatim. */
  final case class Element(
      tag: String,
      attributes: Map[String, String],
      inner: String,
      outer: String,
      start: Int,
      end: Int
  ) {
    def attr(name: String): Option[String] = attributes.get(name)

    def attrOrEmpty(name: String): String = attributes.getOrElse(name, "")
  }

  private val AttributePattern = """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"""".r

  /**
   * Every `tag` element in `xml`, in document order, including nested occurrences
   * (a `<custom-block>` used as an argument of another one must be found too).
   */
  def elements(xml: String, tag: String): List[Element] = {
    val found = List.newBuilder[Element]
    var index = openTagIndex(xml, tag, 0)
    while index >= 0 do
      parseElementAt(xml, index).foreach(found += _)
      index = openTagIndex(xml, tag, index + 1)
    found.result()
  }

  /** Direct child elements of an element's inner content; text nodes are skipped. */
  def children(inner: String): List[Element] = {
    val found = List.newBuilder[Element]
    var index = 0
    while index >= 0 && index < inner.length do
      val next = inner.indexOf('<', index)
      if next < 0 then index = -1
      else
        parseElementAt(inner, next) match
          case Some(element) =>
            found += element
            index = element.end
          case None =>
            index = next + 1
    found.result()
  }

  def child(inner: String, tag: String): Option[Element] =
    children(inner).find(_.tag == tag)

  def unescape(value: String): String =
    value
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&")

  /** Parse the element opening at `pos`; `None` for comments, declarations and malformed input. */
  private def parseElementAt(xml: String, pos: Int): Option[Element] = {
    if pos < 0 || pos >= xml.length || xml.charAt(pos) != '<' then return None
    val nameStart = pos + 1
    if nameStart >= xml.length then return None
    val first = xml.charAt(nameStart)
    if !first.isLetter && first != '_' then return None
    var cursor = nameStart
    while cursor < xml.length && isNameChar(xml.charAt(cursor)) do cursor += 1
    val tag = xml.substring(nameStart, cursor)
    val headerEnd = xml.indexOf('>', cursor)
    if headerEnd < 0 then return None
    val header = xml.substring(cursor, headerEnd)
    val attributes = AttributePattern
      .findAllMatchIn(header)
      .map(matched => matched.group(1) -> unescape(matched.group(2)))
      .toMap
    if header.trim.endsWith("/") then
      Some(Element(tag, attributes, "", xml.substring(pos, headerEnd + 1), pos, headerEnd + 1))
    else
      closeTagBounds(xml, tag, headerEnd + 1).map { (innerEnd, elementEnd) =>
        Element(tag, attributes, xml.substring(headerEnd + 1, innerEnd), xml.substring(pos, elementEnd), pos, elementEnd)
      }
  }

  /** Locate the matching close tag, skipping nested same-name elements. */
  private def closeTagBounds(xml: String, tag: String, from: Int): Option[(Int, Int)] = {
    val close = "</" + tag
    var depth = 1
    var cursor = from
    while depth > 0 do
      val nextOpen = openTagIndex(xml, tag, cursor)
      val nextClose = closeTagIndex(xml, close, cursor)
      if nextClose < 0 then return None
      if nextOpen >= 0 && nextOpen < nextClose then
        val headerEnd = xml.indexOf('>', nextOpen)
        if headerEnd < 0 then return None
        if !xml.substring(nextOpen, headerEnd).trim.endsWith("/") then depth += 1
        cursor = headerEnd + 1
      else
        depth -= 1
        if depth == 0 then
          val headerEnd = xml.indexOf('>', nextClose)
          if headerEnd < 0 then return None
          return Some((nextClose, headerEnd + 1))
        cursor = nextClose + close.length
    None
  }

  /** Index of the next `<tag` whose name ends at a delimiter, so `<block` does not match `<block-definition`. */
  private def openTagIndex(xml: String, tag: String, from: Int): Int = {
    val needle = "<" + tag
    var index = xml.indexOf(needle, from)
    while index >= 0 do
      val after = index + needle.length
      if after >= xml.length then return -1
      val ch = xml.charAt(after)
      if ch == '>' || ch == '/' || ch.isWhitespace then return index
      index = xml.indexOf(needle, index + 1)
    -1
  }

  private def closeTagIndex(xml: String, close: String, from: Int): Int = {
    var index = xml.indexOf(close, from)
    while index >= 0 do
      val after = index + close.length
      if after >= xml.length then return -1
      val ch = xml.charAt(after)
      if ch == '>' || ch.isWhitespace then return index
      index = xml.indexOf(close, index + 1)
    -1
  }

  private def isNameChar(ch: Char): Boolean =
    ch.isLetterOrDigit || ch == '_' || ch == '-' || ch == ':' || ch == '.'
}
