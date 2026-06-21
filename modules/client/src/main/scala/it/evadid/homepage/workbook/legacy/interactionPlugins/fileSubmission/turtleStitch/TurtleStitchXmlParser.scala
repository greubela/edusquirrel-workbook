package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.NodeDomSupport
import org.scalajs.dom
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
    scala.util.Try {
      NodeDomSupport.parseXml(xml) match {
        case Some(document) =>
          val rootNode = Option(document.getElementsByTagName("project").item(0))
            .orElse(Option(document.documentElement).filter(_.nodeName == "project"))
          ParseResult(rootNode.map(node => XmlDocument(fromDom(node))))
        case None =>
          ParseResult(parseStringFallback(xml), Some("DOM parser is unavailable in this runtime"))
      }
    }.recover { case error =>
      ParseResult(parseStringFallback(xml), Some(s"DOM parsing failed (${error.getClass.getSimpleName}); using string parser"))
    }.getOrElse(ParseResult(None, Some("XML parsing failed")))
  }

  private def fromDom(node: dom.Node): XmlElement =
    XmlElement(
      name = node.nodeName,
      attrs = attrs(node),
      text = node.textContent.trim,
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
          val name = attrNode.selectDynamic("name").toString
          val value = attrNode.selectDynamic("value").toString
          Option.when(name.nonEmpty)(name -> value)
        }
      }.toMap
    }
  }

  private def children(parent: dom.Node): List[dom.Node] =
    (0 until parent.childNodes.length).toList
      .flatMap(index => Option(parent.childNodes.item(index)))
      .filter(isElementNode)

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

    XmlDocument(XmlElement("project", projectAttrs, children = List(
      XmlElement("notes", text = textTag(xml, "notes").getOrElse("")),
      XmlElement("scenes", spritesAttrs ++ attrsFromString(xml, "scenes"), children = List(
        XmlElement("scene", sceneAttrs, children = List(
          XmlElement("stage", stageAttrs, children = List(
            textTag(xml, "pentrails").map(value => XmlElement("pentrails", text = value)).toList,
            List(XmlElement("scripts", children = parseScriptsFromString(xml))),
            List(XmlElement("sprites", spritesAttrs, children = List(
              XmlElement("sprite", spriteAttrs, children = List(XmlElement("scripts", children = parseScriptsFromString(xml))))
            )))
          ).flatten)
        ))
      ))
    )))
  }.toOption

  private def parseScriptsFromString(xml: String): List[XmlElement] = {
    val scriptPattern = """(?s)<script[^>]*>(.*?)</script>""".r
    scriptPattern.findAllMatchIn(xml).toList.map { m =>
      XmlElement("script", children = parseBlocksFromString(m.group(1)))
    }
  }

  private def parseBlocksFromString(xml: String): List[XmlElement] = {
    val normalizedXml = """<block\s+([^>]*)/>""".r.replaceAllIn(xml, m => s"<block ${m.group(1)}></block>")
    val blockPattern = """(?s)<block\s+([^>]*)>(.*?)</block>""".r
    blockPattern.findAllMatchIn(normalizedXml).toList.map { m =>
      val body = m.group(2)
      val literals = """(?s)<l>\s*(.*?)\s*</l>""".r.findAllMatchIn(body).toList.map(mm => XmlElement("l", text = mm.group(1).trim))
      val nestedScript = """(?s)<script>(.*?)</script>""".r.findFirstMatchIn(body).map(mm => XmlElement("script", children = parseBlocksFromString(mm.group(1)))).toList
      XmlElement("block", attrsFromAttrText(m.group(1)), children = literals ++ nestedScript)
    }
  }

  private def attrsFromString(xml: String, tag: String): Map[String, String] =
    ("""(?s)<""" + tag + """\b([^>]*)>""").r.findFirstMatchIn(xml).map(m => attrsFromAttrText(m.group(1))).getOrElse(Map.empty)

  private def attrsFromAttrText(attrs: String): Map[String, String] =
    """([:\w-]+)=\"([^\"]*)\""".r.findAllMatchIn(attrs).map(m => m.group(1) -> m.group(2)).toMap

  private def textTag(xml: String, tag: String): Option[String] =
    ("(?s)<" + tag + ">\\s*(.*?)\\s*</" + tag + ">" ).r.findFirstMatchIn(xml).map(_.group(1))
}
