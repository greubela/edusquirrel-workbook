package it.evadid.workbook.model.feedback

/** Selects which router determines the primary issue/template. */
enum RouterMode derives upickle.default.ReadWriter:
  case Heuristic
  case Ml
