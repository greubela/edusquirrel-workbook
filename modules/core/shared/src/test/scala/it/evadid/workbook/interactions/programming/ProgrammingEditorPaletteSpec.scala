package it.evadid.workbook.interactions.programming

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.state.snap.{PaletteLayout, ProgrammingEditorPalette, SnapPaletteCatalog}
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{MaxCommandUsage, RecreateTurtleDidacticConfig, TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog.PaletteTab
import it.evadid.workbook.serialization.WorkbookElementFactory
import upickle.default.*
import munit.FunSuite

class ProgrammingEditorPaletteSpec extends FunSuite {

  test("mixed palettes only reference python-compatible selectors") {
    List(ProgrammingEditorPalette.Default, ProgrammingEditorPalette.PythonCompatibleSnap,
      ProgrammingEditorPalette.BeginnerTurtle, ProgrammingEditorPalette.Embroidery).foreach { palette =>
      palette.layout match
        case PaletteLayout.Mixed(selectors, _, _) =>
          val unknown = selectors.filterNot(SnapPaletteCatalog.pythonCompatibleSelectorSet.contains)
          assertEquals(unknown, Nil, clue = palette.toString)
        case _ => ()
    }
  }
  test("custom mixed layouts preserve selector order and derive compatibility from the selected blocks") {
    val selectors = List("receiveGo", "turn", "forward", "doRepeat")
    val palette = ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(selectors))
    assertEquals(palette.layout, PaletteLayout.Mixed(selectors))
    assert(palette.pythonCompatible)
    assert(!ProgrammingEditorPalette.Custom(PaletteLayout.NativeSnap).pythonCompatible)
    assert(!ProgrammingEditorPalette.Default.pythonCompatible)
    assert(ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(Nil)).pythonCompatible)
  }

  test("mixed palettes may offer predefined module blocks by id, but no unknown ids") {
    val layout = PaletteLayout.Mixed(List("receiveGo", "NumberList.swap", "NumberList.length"))
    assert(ProgrammingEditorPalette.Custom(layout).pythonCompatible)
    intercept[IllegalArgumentException](ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(List("NumberList.sort"))))
    val listPalette = ProgrammingEditorPalette.NumberList.layout
    assert(ProgrammingEditorPalette.NumberList.pythonCompatible)
    listPalette match
      case PaletteLayout.Mixed(selectors, customBlocks, variables) =>
        assert(customBlocks && variables)
        assertEquals(selectors.filter(_.startsWith("NumberList.")),
          List("NumberList.get", "NumberList.set", "NumberList.swap", "NumberList.length"))
      case other => fail(other.toString)
    assertEquals(read[ProgrammingEditorPalette](write[ProgrammingEditorPalette](ProgrammingEditorPalette.NumberList)), ProgrammingEditorPalette.NumberList)
  }

  test("custom catalog palettes retain the selected categories") {
    val layout = PaletteLayout.CatalogTabs(List(PaletteTab.Control, PaletteTab.Motion))
    val palette = ProgrammingEditorPalette.Custom(layout)
    assertEquals(palette.layout, layout)
    assert(palette.pythonCompatible)
  }

  test("invalid custom selectors and ambiguous category lists fail at construction") {
    val error = intercept[IllegalArgumentException] {
      ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(List("forward", "unknownBlock", "right")))
    }
    assert(error.getMessage.contains("unknownBlock"))
    assert(error.getMessage.contains("right")) // canonical method name, not Snap's selector "turn"
    intercept[IllegalArgumentException] {
      ProgrammingEditorPalette.Custom(PaletteLayout.CatalogTabs(Nil))
    }
    intercept[IllegalArgumentException] {
      ProgrammingEditorPalette.Custom(PaletteLayout.CatalogTabs(List(PaletteTab.Motion, PaletteTab.Motion)))
    }
  }

  test("custom palettes roundtrip inside recreate-shape exercises through both definition formats") {
    val layouts = List(PaletteLayout.Mixed(List("receiveGo", "forward", "turn", "doRepeat")),
      PaletteLayout.CatalogTabs(List(PaletteTab.Control, PaletteTab.Motion)),
      PaletteLayout.NativeSnap, PaletteLayout.Mixed(Nil))
    layouts.foreach { layout =>
      val original = TurtleRecreateShapeInteraction("custom-shape", ProgrammingStateRaw(Python, "forward(10)"),
        TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"),
        customExerciseConfig = Some(RecreateTurtleDidacticConfig(
          List(SnapLanguage, Python, Java),
          ProgrammingEditorPalette.Custom(layout),
          MaxCommandUsage.noBounds)))
      val restored = List(
        WorkbookElementFactory.serializerRefBasedJson.deserialize(WorkbookElementFactory.serializerRefBasedJson.serialize(original)),
        WorkbookElementFactory.serializerConstructorLike.deserialize(original.toStringConstructorLike))
      restored.foreach(element => assertEquals(element, original))
      assertEquals(readBinary[ProgrammingEditorPalette](writeBinary(original.exerciseConfig.availablePalette)), original.exerciseConfig.availablePalette)
    }
  }

  test("palette deserialization validates selectors instead of silently dropping unknown blocks") {
    val palette = ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(List("forward")))
    val encoded = write[ProgrammingEditorPalette](palette)
    assertEquals(read[ProgrammingEditorPalette](encoded), palette)
    val invalid = encoded.replace("forward", "unknownBlock")
    intercept[Exception](read[ProgrammingEditorPalette](invalid))
  }

}
