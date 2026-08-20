package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.NodeDomSupport
import org.scalajs.dom

import scala.collection.mutable.ListBuffer
import scala.scalajs.js

object TurtleStitchXmlParser {

  final case class XmlDocument(root: XmlElement)
  final case class XmlElement(
      name: String,
      attrs: Map[String, String] = Map.empty,
      text: String = "",
      children: List[XmlElement] = Nil
  )
  final case class ParseResult(document: Option[XmlDocument], fallbackReason: Option[String] = None)

  def parse(xml: String): ParseResult = {
    // Prefer string parse for Snap block projects — DOM walking crashes on live getProjectXML.
    if xml.contains("<block") || xml.contains("<custom-block") then
      ParseResult(parseStringFallback(xml), None)
    else
      scala.util.Try {
        NodeDomSupport.parseXml(xml) match {
          case Some(document) =>
            val rootNode = Option(document.getElementsByTagName("project").item(0))
              .orElse(Option(document.documentElement).filter(_.nodeName == "project"))
            rootNode match
              case Some(node) =>
                ParseResult(Some(XmlDocument(fromDom(node))))
              case None =>
                ParseResult(parseStringFallback(xml), Some("DOM document had no <project>; using string parser"))
          case None =>
            ParseResult(parseStringFallback(xml), Some("DOM parser is unavailable in this runtime"))
        }
      }.recover { case error =>
        ParseResult(
          parseStringFallback(xml),
          Some(s"DOM parsing failed (${error.getClass.getSimpleName}); using string parser")
        )
      }.getOrElse(ParseResult(None, Some("XML parsing failed")))
  }

  /** String-only parse used when the DOM/model path yields no callable blocks. */
  def parseStringOnly(xml: String): Option[XmlDocument] =
    parseStringFallback(xml)

  private def fromDom(node: dom.Node): XmlElement =
    XmlElement(
      name = Option(node.nodeName).getOrElse(""),
      attrs = attrs(node),
      text = Option(node.textContent).getOrElse("").trim,
      children = children(node).map(fromDom)
    )

  private def attrs(node: dom.Node): Map[String, String] = {
    val attributes = node.asInstanceOf[js.Dynamic].selectDynamic("attributes")
    if (js.isUndefined(attributes) || attributes == null) Map.empty
    else {
      val length = scala.util.Try(attributes.selectDynamic("length").toString.toDouble.toInt).toOption.getOrElse(0)
      (0 until length).toList.flatMap { index =>
        val attrNode = attributes.selectDynamic("item")(index)
        if (js.isUndefined(attrNode) || attrNode == null) None
        else {
          val name = Option(attrNode.selectDynamic("name")).map(_.toString).getOrElse("")
          val value = Option(attrNode.selectDynamic("value")).map(_.toString).getOrElse("")
          Option.when(name.nonEmpty)(name -> value)
        }
      }.toMap
    }
  }

  private def children(parent: dom.Node): List[dom.Node] = {
    val childNodes = parent.childNodes
    if childNodes == null then Nil
    else
      (0 until childNodes.length).toList
        .flatMap(index => Option(childNodes.item(index)))
        .filter(isElementNode)
  }

  private val ElementNodeType = 1

  private def isElementNode(node: dom.Node): Boolean = {
    val nodeTypeValue = scala.util.Try(node.asInstanceOf[js.Dynamic].selectDynamic("nodeType")).toOption
    nodeTypeValue.exists { rawValue =>
      !(js.isUndefined(rawValue) || rawValue == null) &&
        scala.util.Try(rawValue.toString.toDouble.toInt).toOption.contains(ElementNodeType)
    }
  }

  private def parseStringFallback(xml: String): Option[XmlDocument] = scala.util.Try {
    val projectAttrs = attrsFromString(xml, "project")
    val sceneAttrs = attrsFromString(xml, "scene")
    val stageAttrs = attrsFromString(xml, "stage")
    val spritesAttrs = attrsFromString(xml, "sprites")
    val spriteAttrs = attrsFromString(xml, "sprite")
    val scriptElements = parseScriptsFromString(preferredScriptsXml(xml))

    XmlDocument(XmlElement("project", projectAttrs, children = List(
      XmlElement("notes", text = textTag(xml, "notes").getOrElse("")),
      XmlElement("scenes", attrsFromString(xml, "scenes"), children = List(
        XmlElement("scene", sceneAttrs, children = List(
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
      .flatMap { sprites =>
        findFirstTagInnerAnywhere(sprites, "sprite")
          .flatMap(sprite => findFirstTagInnerAnywhere(sprite, "scripts").filter(_.contains("<block")))
          .orElse(findFirstTagInnerAnywhere(sprites, "scripts").filter(_.contains("<block")))
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

  private def parseBlocksFromString(xml: String): List[XmlElement] =
    topLevelTaggedSections(xml, "block").map { case (attrs, body) =>
      XmlElement("block", attrsFromAttrText(attrs), children = parseBlockInputChildren(body))
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
                out += XmlElement("l", text = inner.trim)
              case "bool" =>
                out += XmlElement("bool", text = inner.trim)
              case "script" =>
                out += XmlElement("script", attrsFromAttrText(rawAttrs), children = parseBlocksFromString(inner))
              case "block" =>
                out += XmlElement(
                  "block",
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
    val candidates = List("l", "bool", "script", "block", "list").flatMap { tag =>
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
  private def topLevelTaggedSections(xml: String, tag: String): List[(String, String)] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    val out = ListBuffer.empty[(String, String)]
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
          out += ((rawAttrs.stripSuffix("/").trim, ""))
          i = gt + 1
        else
          val innerStart = gt + 1
          val innerEnd = findMatchingClose(xml, innerStart, open, close)
          if innerEnd < 0 then return out.toList
          out += ((rawAttrs, xml.substring(innerStart, innerEnd)))
          i = innerEnd + close.length
    out.toList
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

  private def attrsFromAttrText(attrs: String): Map[String, String] =
    """([:\w-]+)=\"([^\"]*)\""".r.findAllMatchIn(attrs).map(m => m.group(1) -> m.group(2)).toMap

  private def textTag(xml: String, tag: String): Option[String] =
    ("(?s)<" + tag + ">\\s*(.*?)\\s*</" + tag + ">" ).r.findFirstMatchIn(xml).map(_.group(1))
}
