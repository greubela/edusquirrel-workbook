package it.evadid.workbook.elements.interactionElements.programming

/** Which Snap block palette to show in the programming exercise editor. */
enum ProgrammingEditorPalette:
  /** Snap's native categories (all blocks per category). */
  case Default
  /** Explicit allow-list aligned with Snap ↔ Python roundtrip support. */
  case PythonCompatibleSnap
