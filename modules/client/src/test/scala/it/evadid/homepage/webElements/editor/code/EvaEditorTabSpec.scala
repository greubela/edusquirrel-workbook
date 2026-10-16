package it.evadid.homepage.webElements.editor.code

import munit.FunSuite
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.core.datastructures.language.AppLanguage.*

class EvaEditorTabSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("tab-test"), PrintToStdLogger.printNothing)
  private val tabs = EvaEditorConfig.tabs(logger, EvaEditorConfig.Default)
  test("language choices convert raw matching drafts without parsing them") {
    val java = ProgrammingStateJavaString("class Unfinished {")
    val python = ProgrammingStatePythonString("def unfinished(")
    assertEquals(tabs.find(_.language == Java).get.convert(java), java)
    assertEquals(tabs.find(_.language == Python).get.convert(python), python)
  }
  test("Snap conversion preserves the full typed project and opaque assets") {
    val source = ProgrammingStateSnapXml("<project/>", List("watcher", "comment")).toSnapScalaModel.toOption.get
    assertEquals(tabs.find(_.language == SnapLanguage).get.convert(source), source)
  }
  test("tab metadata follows the configured order without constructing editors") {
    assertEquals(tabs.map(_.language), List(SnapLanguage, Python, Java))
    assertEquals(tabs.map(_.label), List("Snap!", "Python", "Java"))
  }
}
