package it.evadid.vm.code.defining

/** Library semantics, independent of source spelling and target language syntax. */
enum BeIntrinsic derives upickle.default.ReadWriter {
  case Turtle, Print
}
