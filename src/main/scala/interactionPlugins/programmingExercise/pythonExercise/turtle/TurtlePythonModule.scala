package interactionPlugins.programmingExercise.pythonExercise.turtle

object TurtlePythonModule {
  val moduleBootstrapPython: String =
    """
      |import sys
      |import types
      |import _scalajs_turtle_backend as backend
      |
      |class _Screen:
      |    def __getattr__(self, name):
      |        return lambda *args: backend.call_screen(name, *args)
      |
      |class Turtle:
      |    def __init__(self):
      |        self._id = backend.create_turtle()
      |
      |    def __getattr__(self, name):
      |        return lambda *args: backend.call_turtle(self._id, name, *args)
      |
      |_screen = _Screen()
      |_default_id = backend.default_turtle_id()
      |
      |def Screen():
      |    return _screen
      |
      |def __getattr__(name):
      |    return lambda *args: backend.call_turtle(_default_id, name, *args)
      |
      |m = types.ModuleType("turtle")
      |m.Turtle = Turtle
      |m.Screen = Screen
      |m.__getattr__ = __getattr__
      |sys.modules["turtle"] = m
      |""".stripMargin
}
