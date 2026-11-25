package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import munit.FunSuite
import com.raquo.laminar.api.L.{Signal, Val}
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaEditorLabels}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}

class EvaEditorTabSpec extends FunSuite {
  private val tabs = EvaEditorConfig.tabs(EvaEditorConfig.Default)
  test("language choices convert raw matching drafts without parsing them") {
    val java = ProgrammingStateRaw(Java, "class Unfinished {")
    val python = ProgrammingStateRaw(Python, "def unfinished(")
    assertEquals(tabs.find(_.language == Java).get.convert(java, ProgrammingEnvironment.all).finalState.get, java.toJavaStructured)
    assertEquals(tabs.find(_.language == Python).get.convert(python, ProgrammingEnvironment.all).finalState.get, python.toPythonStructured)
  }
  test("Snap conversion preserves the full typed project and opaque assets") {
    val source = ProgrammingStateRaw(SnapLanguage, "<project/>").toSnapScalaModel.toOption.get
    assertEquals(tabs.find(_.language == SnapLanguage).get.convert(source, ProgrammingEnvironment.all).finalState.get, source)
  }
  test("conversions read the draft in the exercise environment") {
    val python = ProgrammingStateRaw(Python, "forward(10)\n")
    def java(environment: ProgrammingEnvironment) = tabs.find(_.language == Java).get.convert(python, environment).finalState.get.toJava.code
    assert(java(ProgrammingEnvironment.all).contains("class Turtle"), java(ProgrammingEnvironment.all))
    assert(!java(ProgrammingEnvironment.of(ProgrammingLibrary.System)).contains("class Turtle"), "an excluded library adds no definition")
  }
  private def current(signal: Signal[String]): String = {
    val owner = new com.raquo.airstream.ownership.ManualOwner
    try signal.observe(using owner).now() finally owner.killSubscriptions()
  }

  test("tab metadata follows the configured order without constructing editors") {
    assertEquals(tabs.map(_.language), List(SnapLanguage, Python, Java, BlockDisplay))
    val labels = EvaEditorLabels.fromContent(id => Val(if id.entryKey == "blockstab" then "Blocks" else "_"))
    assertEquals(tabs.map(tab => current(tab.label(labels))), List("Snap!", "Python", "Java", "Blocks"))
  }
}
