package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.homepage.webElements.HtmlAppElement
import munit.FunSuite
import scala.collection.mutable.ListBuffer

class FullscreenRetentionSpec extends FunSuite {
  private case class Editor(name: String) extends HtmlAppElement {
    override def getDomElement(): Element = div()
  }

  test("closing and reopening the same editor retains it; equal new instances replace it") {
    val first = Editor("same parameters")
    val replacement = Editor("same parameters")
    assertEquals(first, replacement)
    assert(!(first eq replacement))
    val active = Var[Option[HtmlAppElement]](None)
    val rendered = ListBuffer.empty[Option[HtmlAppElement]]
    val owner = new ManualOwner
    try {
      HtmlWorkbookDomElement.retainFullscreenElements(active.signal).foreach(rendered += _)(using owner)
      active.set(Some(first)); active.set(None); active.set(Some(first))
      assertEquals(rendered.size, 2)
      assert(rendered.last.get eq first)
      active.set(None); active.set(Some(replacement))
      assertEquals(rendered.size, 3)
      assert(rendered.last.get eq replacement)
      active.set(None)
      assertEquals(rendered.size, 3)
    } finally owner.killSubscriptions()
  }
}
