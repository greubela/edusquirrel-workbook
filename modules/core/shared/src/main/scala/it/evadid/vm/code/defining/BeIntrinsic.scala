package it.evadid.vm.code.defining

/** Library semantics, independent of source spelling and target language syntax. */
enum BeIntrinsic derives upickle.default.ReadWriter {
  case Turtle, Print, PrintErr
  /** The text behind a URL. */
  case FetchText
  /** Python's `int`, `float`, `str` and `bool` conversions and `abs`; `int` truncates towards zero. */
  case ToInteger, ToNumber, ToText, ToBoolean, Absolute
  /** The larger and the smaller of two numbers. Python's `round` is not shared: Java rounds halves up. */
  case Maximum, Minimum
  /** The square root (Python `math.sqrt`, Java `Math.sqrt`, Snap `sqrt of`). */
  case SquareRoot
  /** A random number in [0, 1) (Python `random.random()`, Java `Math.random()`); not computed statically. */
  case Random
  /** Operations on the provided number list ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  case NumberList
  /** Operations on the provided sudoku grid ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  case Sudoku
}
