package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.{CallbackOp, PythonRunConfig, PythonRunReport}
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingStateSnapXml
import munit.FunSuite
import todomove.`export`.workers.PyodideWorkerClient

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class SnapTurtleCommandExecutionSpec extends FunSuite:

  test("worker URL lookup safely handles an absent optional global configuration") {
    assertEquals(
      PyodideWorkerClient.configuredWorkerUrl(js.Dynamic.literal()),
      "../js/pyodide-worker.js"
    )
    assertEquals(
      PyodideWorkerClient.configuredWorkerUrl(
        js.Dynamic.literal(PYODIDE_WORKER_URL = "../configured/pyodide-worker.js")
      ),
      "../configured/pyodide-worker.js"
    )
  }

  private def executePythonWithRuntimeCalls(
      python: String,
      calls: List[(String, List[Double])]
  ): Future[List[TurtleCommand[Double]]] =
    val executor = new PythonCallbackExecutor:
      override def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
        assertEquals(moduleName, "turtle")
        Future.successful(())

      override def run(code: String, config: PythonRunConfig): Future[PythonRunReport] =
        assertEquals(code, s"from turtle import *\n$python")
        assert(config.resetGlobals)
        Future.successful(PythonRunReport(
          calls.map { case (name, args) =>
            CallbackOp("turtle", name, args.map(_.asInstanceOf[js.Any]).toVector)
          }.toVector,
          stdout = "",
          stderr = ""
        ))

    new PyodideTurtleCommandRunner(executor).execute(python)

  test("derives Python from the supplied current Snap state and forwards execution results") {
    var executed = ""
    val expected = List(TurtleCommand[Double]("forward", List(10.0)))
    val runner = new TurtleCommandRunner:
      override def execute(python: String): Future[List[TurtleCommand[Double]]] =
        executed = python
        Future.successful(expected)

    val state = ProgrammingStateSnapXml.fromProgram(
      BeProgram.fromPythonString("for i in range(1, 4):\n    forward(i * 10)")
    )
    new SnapTurtleCommandExecution(runner).commandsFor(state).map { actual =>
      assertEquals(actual, expected)
      assert(executed.contains("for i in range(1, 3 + 1)"), clue = executed)
      assert(executed.contains("forward(i * 10)"), clue = executed)
    }
  }

  test("decodes ordered Pyodide hooks with canonical names and typed arguments") {
    val callbacks = Vector(
      CallbackOp("turtle", "fd", Vector[js.Any](12.5)),
      CallbackOp("other", "forward", Vector[js.Any](99)),
      CallbackOp("turtle", "pencolor", Vector[js.Any]("#12abef")),
      CallbackOp("turtle", "jump_stitch", Vector[js.Any](true))
    )

    assertEquals(
      PyodideTurtleCommandRunner.commandsFrom(callbacks),
      List(
        TurtleCommand[Double]("forward", List(12.5)),
        TurtleCommand[Double]("color", stringArgs = List("#12abef")),
        TurtleCommand[Double]("jump_stitch", stringArgs = List("true"))
      )
    )
  }

  test("returns the worker's executed calls rather than commands present in Python text") {
    var installedMethods = Seq.empty[String]
    var executedCode = ""
    val executor = new PythonCallbackExecutor:
      override def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
        assertEquals(moduleName, "turtle")
        installedMethods = methodNames
        Future.successful(())

      override def run(code: String, config: PythonRunConfig): Future[PythonRunReport] =
        executedCode = code
        assert(config.resetGlobals)
        Future.successful(PythonRunReport(
          Vector(
            CallbackOp("turtle", "forward", Vector[js.Any](10)),
            CallbackOp("turtle", "forward", Vector[js.Any](20)),
            CallbackOp("turtle", "forward", Vector[js.Any](30))
          ),
          stdout = "",
          stderr = ""
        ))

    new PyodideTurtleCommandRunner(executor)
      .execute("for i in range(1, 4):\n    forward(i * 10)")
      .map { commands =>
        assert(installedMethods.contains("forward"))
        assert(executedCode.startsWith("from turtle import *\n"), clue = executedCode)
        assertEquals(
          commands,
          List(
            TurtleCommand[Double]("forward", List(10.0)),
            TurtleCommand[Double]("forward", List(20.0)),
            TurtleCommand[Double]("forward", List(30.0))
          )
        )
      }
  }

  test("does not turn non-finite numbers into valid command arguments") {
    val callbacks = Vector(CallbackOp("turtle", "forward", Vector[js.Any](Double.NaN)))
    intercept[IllegalArgumentException](PyodideTurtleCommandRunner.commandsFrom(callbacks))
  }

  test("returns the executed branch and iterations for Python containing if and while") {
    val python =
      """distance = 10
        |while distance <= 30:
        |    if distance == 20:
        |        right(90)
        |    else:
        |        forward(distance)
        |    distance = distance + 10""".stripMargin

    executePythonWithRuntimeCalls(
      python,
      List("forward" -> List(10.0), "right" -> List(90.0), "forward" -> List(30.0))
    ).map { commands =>
      assertEquals(commands, List(
        TurtleCommand[Double]("forward", List(10.0)),
        TurtleCommand[Double]("right", List(90.0)),
        TurtleCommand[Double]("forward", List(30.0))
      ))
    }
  }

  test("returns only calls from taken nested if branches across a Python while loop") {
    val python =
      """remaining = 3
        |while remaining > 0:
        |    if remaining > 1:
        |        forward(remaining * 5)
        |    else:
        |        penup()
        |    remaining = remaining - 1""".stripMargin

    executePythonWithRuntimeCalls(
      python,
      List("forward" -> List(15.0), "forward" -> List(10.0), "penup" -> Nil)
    ).map { commands =>
      assertEquals(commands, List(
        TurtleCommand[Double]("forward", List(15.0)),
        TurtleCommand[Double]("forward", List(10.0)),
        TurtleCommand[Double]("penup")
      ))
    }
  }

  test("returns the complete command list from a recursive Python Koch snowflake") {
    val python =
      """def koch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        koch(length / 3, depth - 1)
        |        left(60)
        |        koch(length / 3, depth - 1)
        |        right(120)
        |        koch(length / 3, depth - 1)
        |        left(60)
        |        koch(length / 3, depth - 1)
        |
        |side = 0
        |while side < 3:
        |    if side >= 0:
        |        koch(81, 2)
        |    right(120)
        |    side = side + 1""".stripMargin

    def koch(length: Double, depth: Int): List[(String, List[Double])] =
      if depth == 0 then List("forward" -> List(length))
      else
        koch(length / 3, depth - 1) :::
          List("left" -> List(60.0)) :::
          koch(length / 3, depth - 1) :::
          List("right" -> List(120.0)) :::
          koch(length / 3, depth - 1) :::
          List("left" -> List(60.0)) :::
          koch(length / 3, depth - 1)

    val oneSide = koch(81.0, 2) :+ ("right" -> List(120.0))
    val executed = oneSide ::: oneSide ::: oneSide

    executePythonWithRuntimeCalls(python, executed).map { commands =>
      val expected = executed.map { case (name, args) => TurtleCommand[Double](name, args) }
      assertEquals(commands, expected)
      assertEquals(commands.count(_.name == "forward"), 48)
      assertEquals(commands.length, 96)
    }
  }
