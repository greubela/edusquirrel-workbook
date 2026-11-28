package it.evadid.vm.simulation.python

/** How Pyodide treats turtle calls: the browser has no turtle window, so console runs replace the module. */
enum PythonTurtleCalls derives upickle.default.ReadWriter {
  /** The program's own `turtle` import, e.g. for tests that provide one. */
  @upickle.implicits.key("Native")
  case Native
  /** Each call prints itself, e.g. `forward(100)`, for programs whose drawing is reported as text. */
  @upickle.implicits.key("Logged")
  case Logged
  /** Calls do nothing: the console shows only what the program prints; drawings come from BeExpression. */
  @upickle.implicits.key("Ignored")
  case Ignored
}
