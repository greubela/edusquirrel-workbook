package it.evadid.homepage.webElements.editor.code.SnapEditor

import munit.FunSuite

/**
 * Supported-function examples shown in the Python editor.
 * Delete with SnapPythonPopup.scala if that list goes away.
 */
class SnapPythonPopupSpec extends FunSuite {

  test("overview examples include def user functions") {
    assert(SnapPythonPopup.OverviewExamples.exists(_.contains("def ")), clue = SnapPythonPopup.OverviewExamples)
  }
}
