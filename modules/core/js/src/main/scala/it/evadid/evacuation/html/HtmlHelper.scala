package it.evadid.evacuation.html

import org.scalajs.dom
import org.scalajs.dom.document

object HtmlHelper {


  def clearChildrenFromId(id: String): Unit =
    clearChildrenFromElement(document.getElementById(id))


  def clearChildrenFromElement(element: dom.Element): Unit = {
    val childLength: Int = element.childNodes.length
    (1 to childLength).foreach(_ => element.removeChild(element.firstChild))
  }


}
