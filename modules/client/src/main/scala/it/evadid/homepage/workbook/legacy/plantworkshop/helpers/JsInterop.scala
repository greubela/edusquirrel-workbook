package it.evadid.homepage.workbook.legacy.plantworkshop.helpers

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSGlobal("CodeJar")
class CodeJar(element: dom.Element, highlight: js.Function1[dom.Element, Unit]) extends js.Object {
  def updateCode(code: String): Unit = js.native
  def onUpdate(callback: js.Function1[String, Unit]): Unit = js.native
  def destroy(): Unit = js.native
}

@js.native
@JSGlobal("Prism")
object Prism extends js.Object {
  def highlightElement(element: dom.Element): Unit = js.native
}
