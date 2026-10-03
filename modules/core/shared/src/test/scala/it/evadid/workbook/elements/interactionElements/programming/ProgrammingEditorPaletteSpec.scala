package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite

class ProgrammingEditorPaletteSpec extends FunSuite {

  test("mixed palettes only reference python-compatible selectors") {
    ProgrammingEditorPalette.values.foreach { palette =>
      palette.layout match
        case PaletteLayout.Mixed(selectors) =>
          val unknown = selectors.filterNot(SnapPaletteCatalog.pythonCompatibleSelectorSet.contains)
          assertEquals(unknown, Nil, clue = palette.toString)
        case _ => ()
    }
  }
}
