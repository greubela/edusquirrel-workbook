package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite

class SnapCanvasLayoutSpec extends FunSuite {

  test("the first unplaced script starts at the default origin") {
    val layout = SnapCanvasLayout.placed(List((None, 1, 1)))
    assertEquals(layout.scripts, List(SnapCanvasScript(SnapCanvasLayout.DefaultX, SnapCanvasLayout.DefaultY, 1)))
  }

  test("later unplaced scripts stack under the previous one") {
    val layout = SnapCanvasLayout.placed(List(
      (None, 1, 2),
      (None, 1, 1)
    ))
    val expectedY = SnapCanvasLayout.DefaultY + SnapCanvasLayout.estimatedHeight(2) + SnapCanvasLayout.ScriptGap
    assertEquals(layout.scripts(1), SnapCanvasScript(SnapCanvasLayout.DefaultX, expectedY, 1))
  }

  test("an explicit position is kept and the next default stacks under it") {
    val layout = SnapCanvasLayout.placed(List(
      (Some((70, 80)), 2, 3),
      (None, 1, 1)
    ))
    assertEquals(layout.scripts.head, SnapCanvasScript(70, 80, 2))
    val expectedY = 80 + SnapCanvasLayout.estimatedHeight(3) + SnapCanvasLayout.ScriptGap
    assertEquals(layout.scripts(1), SnapCanvasScript(70, expectedY, 1))
  }

  test("a script with no statements is dropped") {
    val layout = SnapCanvasLayout.placed(List((Some((10, 20)), 0, 4), (None, 1, 1)))
    assertEquals(layout.scripts, List(SnapCanvasScript(SnapCanvasLayout.DefaultX, SnapCanvasLayout.DefaultY, 1)))
  }
}
