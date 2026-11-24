package it.evadid.homepage.webElements.basic

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import munit.FunSuite

class LabelSelectionSpec extends FunSuite {
  /** As in the Java editor: a selection change sets the mode in a follow-up transaction. */
  private final class Scenario {
    val owner = new ManualOwner
    val selection = Var(0)
    val mode = Var("full")
    selection.signal.changes.foreach(_ => mode.set("function"))(using owner)
    val language = Var("en")
    def label(key: String): Signal[String] = language.signal.map(code => s"$key-$code")
    /** Changes in the selection's transaction and again in the follow-up one. */
    val view: Signal[(String, Int)] = mode.signal.combineWith(selection.signal)
    def key(current: (String, Int)): String = if current._1 == "full" then "source" else "function"
  }

  test("a selected label is current after consecutive transactions") {
    val scenario = Scenario()
    val text = LabelSelection(scenario.view, Seq("source", "function"), scenario.label)(current => Some(scenario.key(current))).observe(using scenario.owner)
    assertEquals(text.now(), "source-en")
    scenario.selection.set(1)
    assertEquals(text.now(), "function-en")
    scenario.language.set("de")
    assertEquals(text.now(), "function-de")
    scenario.owner.killSubscriptions()
  }

  test("flatMapSwitch ends on an outdated text in the same situation, which is why labels avoid it") {
    val scenario = Scenario()
    val text = scenario.view.flatMapSwitch(current => scenario.label(scenario.key(current))).observe(using scenario.owner)
    scenario.selection.set(1)
    assertNotEquals(text.now(), "function-en", "if Airstream fixes this, the restriction can be lifted")
    scenario.owner.killSubscriptions()
  }
}
