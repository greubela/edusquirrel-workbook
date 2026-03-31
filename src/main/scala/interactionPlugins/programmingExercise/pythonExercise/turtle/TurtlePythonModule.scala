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
      |        return lambda *args, _backend=backend: _backend.call_screen(name, *args)
      |
      |class Turtle:
      |    def __init__(self):
      |        self._id = backend.create_turtle()
      |
      |    def __getattr__(self, name):
      |        return lambda *args, _backend=backend, _id=self._id: _backend.call_turtle(_id, name, *args)
      |
      |_screen = _Screen()
      |_default_id = backend.default_turtle_id()
      |
      |def Screen():
      |    return _screen
      |
      |def __getattr__(name):
      |    return lambda *args, _backend=backend, _id=_default_id: _backend.call_turtle(_id, name, *args)
      |
      |m = types.ModuleType("turtle")
      |m.Turtle = Turtle
      |m.Screen = Screen
      |m.__getattr__ = __getattr__
      |m.__all__ = [
      |    "Turtle", "Screen",
      |    "forward", "fd",
      |    "backward", "back", "bk",
      |    "left", "lt", "right", "rt",
      |    "goto", "setpos", "setposition", "setx", "sety",
      |    "setheading", "seth", "home",
      |    "penup", "pu", "up", "pendown", "pd", "down",
      |    "pensize", "width",
      |    "pencolor", "fillcolor", "color",
      |    "position", "pos", "xcor", "ycor", "heading", "distance",
      |    "isdown", "showturtle", "st", "hideturtle", "ht", "isvisible",
      |    "clear", "reset"
      |]
      |sys.modules["turtle"] = m
      |""".stripMargin
}
