package it.evadid.vm.code.defining

/** Library semantics, independent of source spelling and target language syntax. */
enum BeIntrinsic derives upickle.default.ReadWriter {
  @upickle.implicits.key("Turtle") case Turtle
  @upickle.implicits.key("Print") case Print
  @upickle.implicits.key("PrintErr") case PrintErr
  /** The text behind a URL. */
  @upickle.implicits.key("FetchText")
  case FetchText
  /** Python's `int`, `float`, `str` and `bool` conversions and `abs`; `int` truncates towards zero. */
  @upickle.implicits.key("ToInteger") case ToInteger
  @upickle.implicits.key("ToNumber") case ToNumber
  @upickle.implicits.key("ToText") case ToText
  @upickle.implicits.key("ToBoolean") case ToBoolean
  @upickle.implicits.key("Absolute") case Absolute
  /** The larger and the smaller of two numbers. Python's `round` is not shared: Java rounds halves up. */
  @upickle.implicits.key("Maximum") case Maximum
  @upickle.implicits.key("Minimum") case Minimum
  /** The square root (Python `math.sqrt`, Java `Math.sqrt`, Snap `sqrt of`). */
  @upickle.implicits.key("SquareRoot")
  case SquareRoot
  /** A random number in [0, 1) (Python `random.random()`, Java `Math.random()`); not computed statically. */
  @upickle.implicits.key("Random")
  case Random
  /** Operations on the provided number list ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  @upickle.implicits.key("NumberList")
  case NumberList
  /** Operations on the provided sudoku grid ([[it.evadid.vm.simulation.data.DataLibraries]]). */
  @upickle.implicits.key("Sudoku")
  case Sudoku
}
