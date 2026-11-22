package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom
import scala.scalajs.js

class StepNavigatorSpec extends EditorDomSuite {
  private def text(key: String): Signal[String] = Val(if key == "stepPosition" then "Step {step} of {total}" else key)

  private final class Fixture(initialTotal: Int) {
    val total = Var(initialTotal)
    val navigator = new StepNavigator(total.signal, text)
    val shown = Var(-1)
    val element = div(navigator.element, navigator.step --> shown.writer)
    def root: dom.Element = element.ref.firstElementChild
    def button(key: String): dom.HTMLButtonElement = root.querySelector(s"button[title='$key']").asInstanceOf[dom.HTMLButtonElement]
    def position: String = root.querySelector(".step-navigator__position").textContent
    def key(name: String): Unit = root.dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.window.KeyboardEvent)(
      "keydown", js.Dynamic.literal(key = name, bubbles = true)).asInstanceOf[dom.Event])
  }

  test("starts at the last step and moves with the buttons, disabling them at the ends") {
    val fixture = Fixture(3)
    val close = mount(fixture.element)
    try {
      assertEquals((fixture.shown.now(), fixture.position), (3, "3 / 3"))
      assert(fixture.button("stepNext").disabled && fixture.button("stepLast").disabled)
      fixture.button("stepPrevious").click()
      assertEquals(fixture.shown.now(), 2)
      fixture.button("stepFirst").click()
      assertEquals((fixture.shown.now(), fixture.position), (0, "0 / 3"))
      assert(fixture.button("stepFirst").disabled && fixture.button("stepPrevious").disabled && !fixture.button("stepNext").disabled)
      fixture.button("stepNext").click()
      assertEquals(fixture.shown.now(), 1)
      fixture.button("stepLast").click()
      assertEquals(fixture.shown.now(), 3)
    } finally close()
  }

  test("arrow keys, Home and End move within the bounds; the range input selects a step") {
    val fixture = Fixture(4)
    val close = mount(fixture.element)
    try {
      fixture.key("ArrowRight")
      assertEquals(fixture.shown.now(), 4, "cannot move beyond the last step")
      fixture.key("Home")
      fixture.key("ArrowLeft")
      assertEquals(fixture.shown.now(), 0)
      fixture.key("ArrowRight")
      assertEquals(fixture.shown.now(), 1)
      fixture.key("End")
      assertEquals(fixture.shown.now(), 4)
      val range = fixture.root.querySelector("input[type='range']").asInstanceOf[dom.HTMLInputElement]
      assertEquals((range.max, range.getAttribute("aria-label")), ("4", "Step 4 of 4"))
      range.value = "2"
      range.dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.window.Event)("input", js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])
      assertEquals(fixture.shown.now(), 2)
    } finally close()
  }

  test("a new execution jumps to its last step, and an empty one shows the initial state") {
    val fixture = Fixture(5)
    val close = mount(fixture.element)
    try {
      fixture.button("stepFirst").click()
      fixture.total.set(7)
      assertEquals((fixture.shown.now(), fixture.position), (7, "7 / 7"))
      fixture.total.set(0)
      assertEquals(fixture.shown.now(), 0)
      assert(fixture.button("stepPrevious").disabled && fixture.button("stepNext").disabled)
    } finally close()
  }
}
