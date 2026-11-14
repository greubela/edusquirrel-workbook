package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.PaletteTab
import upickle.default.*

/** How a programming palette is built from the catalogs. */
enum PaletteLayout derives ReadWriter:
  /** Snap's native categories (all blocks per category). */
  case NativeSnap
  /** An ordered subset of [[SnapPaletteCatalog]] tabs. */
  case CatalogTabs(tabs: List[PaletteTab])
  /** One mixed tab, blocks in the given Snap selector order. */
  case Mixed(selectors: List[String])

  def pythonCompatible: Boolean = {
    val supported = SnapPaletteCatalog.pythonCompatibleSelectorSet
    this match
      case NativeSnap => false
      case CatalogTabs(tabs) => tabs.flatMap(SnapPaletteCatalog.selectorsForTab).forall(supported.contains)
      case Mixed(selectors) => selectors.forall(supported.contains)
  }

object PaletteLayout {
  /** Validate exercise-authored layouts before they reach the native editor. */
  private[snap] def validated(layout: PaletteLayout): PaletteLayout = {
    layout match {
      case PaletteLayout.NativeSnap => ()
      case PaletteLayout.CatalogTabs(tabs) =>
        require(tabs.nonEmpty, "A catalog palette needs at least one tab; use Mixed(Nil) for an empty palette.")
        require(tabs.distinct.size == tabs.size, "Catalog palette tabs must be unique.")
      case PaletteLayout.Mixed(selectors) =>
        val supported = SnapPaletteCatalog.pythonCompatibleSelectorSet
        val unknown = selectors.filterNot(supported.contains).distinct
        require(unknown.isEmpty, s"Unsupported custom palette selectors: ${unknown.mkString(", ")}. Use Snap selectors from SnapPaletteCatalog.")
    }
    layout
  }
}

/** Which Snap block palette to show in the programming exercise editor. */
enum ProgrammingEditorPalette(val layout: PaletteLayout) derives ReadWriter:
  /** Snap's native categories (all blocks per category). */
  case Default extends ProgrammingEditorPalette(PaletteLayout.NativeSnap)
  /** Explicit allow-list aligned with Snap ↔ Python roundtrip support. */
  case PythonCompatibleSnap extends ProgrammingEditorPalette(PaletteLayout.CatalogTabs(SnapPaletteCatalog.TabOrder))
  /** Beginner turtle subset: start, repeat, motion, and pen only. */
  case BeginnerTurtle extends ProgrammingEditorPalette(PaletteLayout.Mixed(List(
    "receiveGo", "doRepeat", "forward", "turn", "gotoXY", "setHeading", "clear", "up", "down"
  )))
  /** Embroidery workbook palette (Python-compatible plus stitch blocks). */
  case Embroidery extends ProgrammingEditorPalette(PaletteLayout.CatalogTabs(List(
    PaletteTab.Motion, PaletteTab.Pen, PaletteTab.Embroidery, PaletteTab.Control
  )))
  /** Exercise-specific layout, serialized with the exercise rather than defined in the client. */
  case Custom(customLayout: PaletteLayout) extends ProgrammingEditorPalette(PaletteLayout.validated(customLayout))

  /** Native palettes include blocks that Python cannot represent. */
  def pythonCompatible: Boolean = layout.pythonCompatible
