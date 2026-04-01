package interactionPlugins.fileSubmission

import org.scalajs.dom

import scala.scalajs.js

object NodeDomSupport {

  def parseXml(xml: String): Option[dom.Document] = scala.util.Try {
    val parserCtor = js.Dynamic.global.selectDynamic("DOMParser")
    if (js.isUndefined(parserCtor) || parserCtor == null) None
    else Some(new dom.DOMParser().parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType]))
  }.toOption.flatten

  def serializeXml(node: dom.Node): Option[String] = scala.util.Try {
    val serializerCtor = js.Dynamic.global.selectDynamic("XMLSerializer")
    if (js.isUndefined(serializerCtor) || serializerCtor == null) None
    else Some(new dom.XMLSerializer().serializeToString(node))
  }.toOption.flatten
}
