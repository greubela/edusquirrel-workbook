package it.evadid.vm.conversion.snapmodelxml

import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*

/** Lossless XML emission retains the lexical spelling carried by the immutable Snap model. */
object SnapModelToXml {
  extension (attribute: Attribute)
    def toXml: String = attribute.leading + attribute.name + attribute.beforeEquals + "=" +
      attribute.afterEquals + attribute.quote + attribute.rawValue + attribute.quote

  extension (element: Element)
    def toXml: String = {
      val start = "<" + element.tag + element.attributes.map(_.toXml).mkString + element.trailing
      if element.selfClosing && element.children.isEmpty then start + "/>"
      else start + ">" + element.children.map(_.toXml).mkString +
        (if element.closing.nonEmpty then element.closing else "</" + element.tag + ">")
    }

  extension (node: Node)
    def toXml: String = node match {
      case Text(raw) => raw
      case CData(value) => "<![CDATA[" + value + "]]>"
      case XmlComment(value) => "<!--" + value + "-->"
      case ProcessingInstruction(value) => "<?" + value + "?>"
      case Declaration(raw) => raw
      case value: SnapElement => value.element.toXml
    }

  extension (document: Document)
    def toXml: String = document.nodes.map(_.toXml).mkString
}
