package it.evadid.vm.modules

/** Console events retain stdout/stderr ordering independently of drawing commands. */
enum SystemCommand(val text: String) {
  case PrintToOut(value: String) extends SystemCommand(value)
  case PrintToErr(value: String) extends SystemCommand(value)
}
