package it.evadid.vm.code.defining

/** Library semantics, independent of source spelling and target language syntax. */
enum BeIntrinsic derives upickle.default.ReadWriter {
  case Turtle, Print
  /** The text behind a URL. */
  case FetchText
  /** Python's `int`, `float`, `str` and `bool` conversions and `abs`; `int` truncates towards zero. */
  case ToInteger, ToNumber, ToText, ToBoolean, Absolute
}
