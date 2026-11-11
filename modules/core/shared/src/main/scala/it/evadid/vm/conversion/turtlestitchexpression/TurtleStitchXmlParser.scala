package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel

import scala.collection.mutable.ListBuffer

object TurtleStitchXmlParser {

  final case class XmlDocument(root: XmlElement) derives upickle.default.ReadWriter
  final case class XmlElement(
      name: String,
      attrs: Map[String, String] = Map.empty,
      text: String = "",
      children: List[XmlElement] = Nil
  ) derives upickle.default.ReadWriter
  final case class ParseResult(document: Option[XmlDocument], fallbackReason: Option[String] = None) derives upickle.default.ReadWriter

  def parse(xml: String): ParseResult =
    if xml.contains("<block") || xml.contains("<custom-block") then
      ParseResult(parseStringFallback(xml))
    else {
      def element(node: SnapScalaModel.SnapElement): XmlElement =
        XmlElement(node.element.tag, node.element.attributes.map(a => a.name -> a.value).toMap,
          node.text.trim, node.element.elements.map(element).toList)
      SnapScalaXmlCodec.parse(xml) match {
        case Right(document) => ParseResult(Some(XmlDocument(element(document.root))))
        case Left(message) => ParseResult(None, Some(message))
      }
    }

  private def parseStringFallback(xml: String): Option[XmlDocument] = scala.util.Try {
    val projectAttrs = attrsFromString(xml, "project")
    val sceneAttrs = attrsFromString(xml, "scene")
    val stageAttrs = attrsFromString(xml, "stage")
    val spritesAttrs = attrsFromString(xml, "sprites")
    val spriteAttrs = attrsFromString(xml, "sprite")
    val scriptElements = parseScriptsFromString(preferredScriptsXml(xml))
    val customBlockElements = parseBlockDefinitionsFromString(xml)

    XmlDocument(XmlElement("project", projectAttrs, children = List(
      XmlElement("notes", text = textTag(xml, "notes").getOrElse("")),
      XmlElement("scenes", attrsFromString(xml, "scenes"), children = List(
        XmlElement("scene", sceneAttrs, children = List(
          XmlElement("blocks", children = customBlockElements),
          XmlElement("stage", stageAttrs, children = List(
            textTag(xml, "pentrails").map(value => XmlElement("pentrails", text = value)).toList,
            List(XmlElement("scripts")),
            List(XmlElement("sprites", spritesAttrs, children = List(
              XmlElement("sprite", spriteAttrs, children = List(XmlElement("scripts", children = scriptElements)))
            )))
          ).flatten)
        ))
      ))
    )))
  }.toOption

  /**
   * Prefer sprite `<scripts>` that contain blocks (stage often has an empty `<scripts>` first).
   */
  private def preferredScriptsXml(xml: String): String =
    findFirstTagInnerAnywhere(xml, "sprites")
      .flatMap { spritesXml =>
        findAllTagInnersAnywhere(spritesXml, "sprite")
          .iterator
          .flatMap(sprite => findAllTagInnersAnywhere(sprite, "scripts"))
          .find(_.contains("<block"))
          .orElse(findAllTagInnersAnywhere(spritesXml, "scripts").find(_.contains("<block")))
      }
      // Never fall back to the first (often empty) stage <scripts></scripts>.
      .orElse(findAllTagInnersAnywhere(xml, "scripts").find(_.contains("<block")))
      .getOrElse(xml)

  private def findAllTagInnersAnywhere(xml: String, tag: String): List[String] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    val out = ListBuffer.empty[String]
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return out.toList
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return out.toList
        val rawAttrs = xml.substring(afterTag, gt).trim
        if rawAttrs.endsWith("/") then
          out += ""
          i = gt + 1
        else
          val innerStart = gt + 1
          val innerEnd = findMatchingClose(xml, innerStart, open, close)
          if innerEnd < 0 then return out.toList
          out += xml.substring(innerStart, innerEnd)
          i = innerEnd + close.length
    out.toList
  }

  /** First `<tag>...</tag>` inner text anywhere in `xml` (not only top-level). */
  private def findFirstTagInnerAnywhere(xml: String, tag: String): Option[String] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return None
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return None
        val rawAttrs = xml.substring(afterTag, gt).trim
        if rawAttrs.endsWith("/") then return Some("")
        val innerStart = gt + 1
        val innerEnd = findMatchingClose(xml, innerStart, open, close)
        if innerEnd < 0 then return None
        return Some(xml.substring(innerStart, innerEnd))
    None
  }

  private def parseScriptsFromString(xml: String): List[XmlElement] =
    topLevelTaggedSections(xml, "script").map { case (attrs, body) =>
      XmlElement("script", attrsFromAttrText(attrs), children = parseBlocksFromString(body))
    }

  private def parseBlocksFromString(xml: String): List[XmlElement] = {
    val withIndex = List("custom-block", "block").flatMap { tag =>
      topLevelTaggedSectionsIndexed(xml, tag).map { case (idx, attrs, body) => (idx, tag, attrs, body) }
    }.sortBy(_._1)
    withIndex.map { case (_, tag, attrs, body) =>
      XmlElement(tag, attrsFromAttrText(attrs), children = parseBlockInputChildren(body))
    }
  }

  private def parseBlockDefinitionsFromString(xml: String): List[XmlElement] = {
    val blocksInner = findAllTagInnersAnywhere(xml, "blocks").find(_.contains("<block-definition")).getOrElse("")
    topLevelTaggedSections(blocksInner, "block-definition").map { case (attrs, body) =>
      XmlElement("block-definition", attrsFromAttrText(attrs), children = parseBlockDefinitionChildren(body))
    }
  }

  private def parseBlockDefinitionChildren(body: String): List[XmlElement] = {
    val inputs = findFirstTagInnerAnywhere(body, "inputs").map { inner =>
      XmlElement(
        "inputs",
        children = topLevelTaggedSections(inner, "input").map { case (attrs, text) =>
          XmlElement("input", attrsFromAttrText(attrs), text = SnapXmlParser.unescape(text))
        }
      )
    }.toList
    val scriptsSpan = firstTagSpan(body, "scripts")
    val scriptsWrapper = scriptsSpan.map { case (_, _, inner) =>
      XmlElement("scripts", children = parseScriptsFromString(inner))
    }.toList
    val directScripts = topLevelTaggedSectionsIndexed(body, "script").collect {
      case (idx, attrs, inner) if scriptsSpan.forall { case (from, to, _) => idx < from || idx >= to } =>
        XmlElement("script", attrsFromAttrText(attrs), children = parseBlocksFromString(inner))
    }
    inputs ++ directScripts ++ scriptsWrapper
  }

  /** Top-level block inputs in document order (`<l>`, `<block>`, `<script>`, `<list>`, `<bool>`). */
  private def parseBlockInputChildren(body: String): List[XmlElement] =
    parseOrderedBlockInputChildren(body)

  private def parseOrderedBlockInputChildren(body: String): List[XmlElement] = {
    val out = ListBuffer.empty[XmlElement]
    var i = 0
    while i < body.length do
      findNextInputTag(body, i) match
        case None => return out.toList
        case Some((start, tag, open, close)) =>
          val afterTag = start + open.length
          val gt = body.indexOf('>', afterTag)
          if gt < 0 then return out.toList
          val rawAttrs = body.substring(afterTag, gt).trim
          if rawAttrs.endsWith("/") then
            tag match
              case "block" =>
                out += XmlElement("block", attrsFromAttrText(rawAttrs.stripSuffix("/").trim))
              case _ => ()
            i = gt + 1
          else
            val innerStart = gt + 1
            val innerEnd = findMatchingClose(body, innerStart, open, close)
            if innerEnd < 0 then return out.toList
            val inner = body.substring(innerStart, innerEnd)
            tag match
              case "l" =>
                out += XmlElement("l", text = SnapXmlParser.unescape(inner.trim))
              case "bool" =>
                out += XmlElement("bool", text = SnapXmlParser.unescape(inner.trim))
              case "color" =>
                out += XmlElement("color", text = SnapXmlParser.unescape(inner.trim))
              case "script" =>
                out += XmlElement("script", attrsFromAttrText(rawAttrs), children = parseBlocksFromString(inner))
              case "block" | "custom-block" =>
                out += XmlElement(
                  tag,
                  attrsFromAttrText(rawAttrs),
                  children = parseOrderedBlockInputChildren(inner)
                )
              case "list" =>
                out += XmlElement("list", children = parseOrderedBlockInputChildren(inner))
              case _ => ()
            i = innerEnd + close.length
    out.toList
  }

  private def findNextInputTag(xml: String, from: Int): Option[(Int, String, String, String)] = {
    val candidates = List("l", "bool", "color", "script", "block", "custom-block", "list").flatMap { tag =>
      val open = s"<$tag"
      val idx = xml.indexOf(open, from)
      if idx < 0 then None
      else
        val afterTag = idx + open.length
        if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then None
        else Some((idx, tag, open, s"</$tag>"))
    }
    candidates.minByOption(_._1)
  }

  /**
   * Depth-aware split into top-level `<tag ...>inner</tag>` / self-closing sections.
   * Avoids nested-regex breakage on Snap script/block trees.
   */
  private def topLevelTaggedSections(xml: String, tag: String): List[(String, String)] =
    topLevelTaggedSectionsIndexed(xml, tag).map { case (_, attrs, body) => (attrs, body) }

  private def topLevelTaggedSectionsIndexed(xml: String, tag: String): List[(Int, String, String)] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    val out = ListBuffer.empty[(Int, String, String)]
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return out.toList
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return out.toList
        val rawAttrs = xml.substring(afterTag, gt).trim
        if rawAttrs.endsWith("/") then
          out += ((start, rawAttrs.stripSuffix("/").trim, ""))
          i = gt + 1
        else
          val innerStart = gt + 1
          val innerEnd = findMatchingClose(xml, innerStart, open, close)
          if innerEnd < 0 then return out.toList
          out += ((start, rawAttrs, xml.substring(innerStart, innerEnd)))
          i = innerEnd + close.length
    out.toList
  }

  /** First `<tag>...</tag>` span: open start, exclusive end, inner text. */
  private def firstTagSpan(xml: String, tag: String): Option[(Int, Int, String)] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return None
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return None
        val rawAttrs = xml.substring(afterTag, gt).trim
        if rawAttrs.endsWith("/") then return Some((start, gt + 1, ""))
        val innerStart = gt + 1
        val innerEnd = findMatchingClose(xml, innerStart, open, close)
        if innerEnd < 0 then return None
        return Some((start, innerEnd + close.length, xml.substring(innerStart, innerEnd)))
    None
  }

  private def isTagNameEnd(ch: Char): Boolean =
    ch.isWhitespace || ch == '>' || ch == '/'

  private def findMatchingClose(xml: String, from: Int, open: String, close: String): Int = {
    var depth = 1
    var i = from
    while i < xml.length && depth > 0 do
      val nextOpen = xml.indexOf(open, i)
      val nextClose = xml.indexOf(close, i)
      if nextClose < 0 then return -1
      if nextOpen >= 0 && nextOpen < nextClose then
        val after = nextOpen + open.length
        if after < xml.length && isTagNameEnd(xml.charAt(after)) then
          val gt = xml.indexOf('>', after)
          val selfClosing = gt >= 0 && xml.substring(after, gt + 1).contains("/")
          if !selfClosing then depth += 1
          i = if gt >= 0 then gt + 1 else math.max(after, nextOpen + 1)
        else
          i = math.max(after, nextOpen + 1)
      else
        depth -= 1
        if depth == 0 then return nextClose
        i = nextClose + close.length
    -1
  }

  private def attrsFromString(xml: String, tag: String): Map[String, String] =
    ("""(?s)<""" + tag + """\b([^>]*)>""").r.findFirstMatchIn(xml).map(m => attrsFromAttrText(m.group(1))).getOrElse(Map.empty)

  private def attrsFromAttrText(attrs: String): Map[String, String] = {
    val parsed = """([:\w-]+)=\"([^\"]*)\"""".r
      .findAllMatchIn(attrs)
      .map { m =>
        val raw = m.group(2)
        val unescaped = SnapXmlParser.unescape(raw)
        m.group(1) -> unescaped
      }
      .toMap
    parsed
  }

  private def textTag(xml: String, tag: String): Option[String] =
    ("(?s)<" + tag + ">\\s*(.*?)\\s*</" + tag + ">" ).r.findFirstMatchIn(xml).map(_.group(1))
}
