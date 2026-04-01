package interactionPlugins.fileSubmission

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object NodeDomSupport {

  @js.native
  @JSImport("@xmldom/xmldom", "DOMParser")
  private class XmldomParser(options: js.UndefOr[js.Object] = js.undefined) extends js.Object {
    def parseFromString(xml: String, mimeType: String): dom.Document = js.native
  }

  @js.native
  @JSImport("@xmldom/xmldom", "XMLSerializer")
  private class XmldomSerializer extends js.Object {
    def serializeToString(node: dom.Node): String = js.native
  }

  def parseXml(xml: String): Option[dom.Document] =
    parseWithGlobalDomParser(xml).orElse(parseWithXmldom(xml))

  def serializeXml(node: dom.Node): Option[String] =
    serializeWithGlobalXmlSerializer(node).orElse(serializeWithXmldom(node))

  private def parseWithGlobalDomParser(xml: String): Option[dom.Document] = scala.util.Try {
    val parserCtor = js.Dynamic.global.selectDynamic("DOMParser")
    if (js.isUndefined(parserCtor) || parserCtor == null) None
    else Some(new dom.DOMParser().parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType]))
  }.toOption.flatten

  private def parseWithXmldom(xml: String): Option[dom.Document] =
    scala.util.Try {
      val silentHandler = js.Dynamic.literal(
        warning = (_: String) => (),
        error = (_: String) => (),
        fatalError = (_: String) => ()
      )
      val options = js.Dynamic.literal(errorHandler = silentHandler).asInstanceOf[js.Object]
      new XmldomParser(options).parseFromString(xml, "text/xml")
    }.toOption

  private def serializeWithGlobalXmlSerializer(node: dom.Node): Option[String] = scala.util.Try {
    val serializerCtor = js.Dynamic.global.selectDynamic("XMLSerializer")
    if (js.isUndefined(serializerCtor) || serializerCtor == null) None
    else Some(new dom.XMLSerializer().serializeToString(node))
  }.toOption.flatten

  private def serializeWithXmldom(node: dom.Node): Option[String] =
    scala.util.Try(new XmldomSerializer().serializeToString(node)).toOption
}
