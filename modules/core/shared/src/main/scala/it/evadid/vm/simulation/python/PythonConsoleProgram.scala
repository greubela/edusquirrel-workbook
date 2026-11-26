package it.evadid.vm.simulation.python

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

/** Runs a Python file with console-only turtle methods, restoring the interpreter's module afterward. */
object PythonConsoleProgram {
  /** `source` with a stub `turtle` module whose calls print themselves (`log`) or do nothing. */
  def wrap(source: String, log: Boolean = true): String = {
    val printCalls = if log then "True" else "False"
    val names = ujson.Arr.from(SnapTurtleCatalog.AllowedPythonNames.toSeq.sorted).render()
    val literal = ujson.Str(source).render()
    s"""def __edusquirrel_run_program():
       |    import sys
       |    import types
       |    import builtins
       |    turtle = types.ModuleType("turtle")
       |    def logged_call(name):
       |        def call(*args, **kwargs):
       |            values = [builtins.repr(value) for value in args]
       |            values.extend(key + "=" + builtins.repr(value) for key, value in kwargs.items())
       |            if $printCalls:
       |                builtins.print(name + "(" + ", ".join(values) + ")")
       |        return call
       |    turtle.__all__ = $names
       |    for name in turtle.__all__:
       |        setattr(turtle, name, logged_call(name))
       |    previous = sys.modules.get("turtle")
       |    had_previous = "turtle" in sys.modules
       |    namespace = globals()
       |    namespace["__name__"] = "__main__"
       |    namespace.setdefault("println", builtins.print)
       |    for name in turtle.__all__:
       |        namespace.setdefault(name, getattr(turtle, name))
       |    sys.modules["turtle"] = turtle
       |    try:
       |        exec(compile($literal, "<workbook>", "exec"), namespace, namespace)
       |    finally:
       |        if had_previous:
       |            sys.modules["turtle"] = previous
       |        else:
       |            sys.modules.pop("turtle", None)
       |__edusquirrel_run_program()
       |""".stripMargin
  }
}
