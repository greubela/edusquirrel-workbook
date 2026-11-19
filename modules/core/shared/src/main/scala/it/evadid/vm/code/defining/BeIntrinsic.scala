package it.evadid.vm.code.defining

/** Library semantics, independent of source spelling and target language syntax. */
enum BeIntrinsic derives upickle.default.ReadWriter {
  case Turtle, Print
  /** Operations on the provided number list ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  case NumberList
  /** Operations on the provided sudoku grid ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  case Sudoku
}
