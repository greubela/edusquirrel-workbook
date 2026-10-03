package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.PaletteTab
import upickle.default.*

/** How a programming palette is built from the catalogs. */
enum PaletteLayout:
  /** Snap's native categories (all blocks per category). */
  case NativeSnap
  /** An ordered subset of [[SnapPaletteCatalog]] tabs. */
  case CatalogTabs(tabs: List[PaletteTab])
  /** One mixed tab, blocks in the given selector order. */
  case Mixed(selectors: List[String])

/** Which Snap block palette to show in the programming exercise editor. */
enum ProgrammingEditorPalette derives ReadWriter:
  /** Snap's native categories (all blocks per category). */
  case Default
  /** Explicit allow-list aligned with Snap ↔ Python roundtrip support. */
  case PythonCompatibleSnap
  /** Beginner turtle subset: start, repeat, motion, and pen only. */
  case BeginnerTurtle
  /** Motion, pen, embroidery stitches, and control — no operators or variables. */
  case Embroidery

  /**
   * Whether every block this palette offers survives a Snap ↔ Python switch.
   * Native Snap categories include blocks Python cannot represent.
   */
  def pythonCompatible: Boolean = this match
    case Default => false
    case PythonCompatibleSnap | BeginnerTurtle | Embroidery => true

  /** Block selection for this palette. The client turns this into editor tabs. */
  def layout: PaletteLayout = this match
    case Default =>
      PaletteLayout.NativeSnap
    case PythonCompatibleSnap =>
      PaletteLayout.CatalogTabs(SnapPaletteCatalog.TabOrder)
    case Embroidery =>
      PaletteLayout.CatalogTabs(List(
        PaletteTab.Motion,
        PaletteTab.Pen,
        PaletteTab.Embroidery,
        PaletteTab.Control
      ))
    case BeginnerTurtle =>
      PaletteLayout.Mixed(List(
        "receiveGo",
        "doRepeat",
        "forward",
        "turn",
        "gotoXY",
        "setHeading",
        "clear",
        "up",
        "down"
      ))
