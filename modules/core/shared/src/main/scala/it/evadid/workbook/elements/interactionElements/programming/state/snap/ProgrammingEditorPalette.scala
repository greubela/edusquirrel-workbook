package it.evadid.workbook.elements.interactionElements.programming.state.snap

import upickle.default.*

/** Which Snap block palette to show in the programming exercise editor. */
enum ProgrammingEditorPalette derives ReadWriter:
  /** Snap's native categories (all blocks per category). */
  case Default
  /** Explicit allow-list aligned with Snap ↔ Python roundtrip support. */
  case PythonCompatibleSnap
  /** Beginner turtle subset: start, repeat, motion, and pen only. */
  case BeginnerTurtle
  /** Embroidery workbook palette (Python-compatible plus stitch blocks). */
  case Embroidery
