package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.{CallbackOp, PythonRunConfig, PythonRunReport}
import it.evadid.vm.BeProgram
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSemantics, JavaTurtleSource, JavaTurtleStructure, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateSnapXml
import munit.FunSuite
import todomove.`export`.workers.PyodideWorkerClient

import java.util.concurrent.{CancellationException, TimeoutException}
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

class SnapTurtleCommandExecutionSpec extends FunSuite:

  private lazy val javaFixture: P.Program =
    val source =
      """class Drawing {
        |  static void draw(int size, boolean turn) {
        |    Turtle.forward(size);
        |    if (turn) { Turtle.turnRight(90); }
        |  }
        |  public static void main(String[] args) { draw(40, true); }
        |}""".stripMargin
    (for
      parsed <- JavaTurtleSource.parse(source)
      structure <- JavaTurtleStructure.check(parsed)
      checked <- JavaTurtleSemantics.check(structure)
      resolved <- R.resolve(checked)
      program <- P.adapt(resolved)
    yield program).fold(problem => fail(problem.message), identity)

  private lazy val javaDoubleFixture: P.Program = P.compile(
    """class Drawing {
      |  static void draw(double size, int depth) {
      |    Turtle.forward(size);
      |    Turtle.turnRight(22.5);
      |  }
      |  public static void main(String[] args) { draw(0.25, 1); }
      |}""".stripMargin).fold(problem => fail(problem.message), identity)

  private def delayed(milliseconds: Int): Future[Unit] =
    val result = Promise[Unit]()
    setTimeout(milliseconds)(result.success(()))
    result.future

  private def failedWith[A](result: Future[A])(check: Throwable => Unit): Future[Unit] =
    result.failed.map(check)

  private def javaReport(
      status: String = "Completed",
      problem: String = "null",
      commands: String = "[]",
      steps: String = "2"
  ): PythonRunReport =
    PythonRunReport(Vector.empty,
      s"""{"status":"$status","problem":$problem,"commands":$commands,"steps":$steps}""", "")

  private val completedJava = T.Execution(T.Status.Completed, Vector.empty, 2)

  private def pythonReport(distance: Double = 10.0): PythonRunReport =
    PythonRunReport(Vector(CallbackOp("turtle", "forward", Vector[js.Any](distance))), "", "")

  private class ControlledPythonExecutor(initiallyReady: Boolean = false) extends PythonCallbackExecutor:
    val readiness = Promise[Unit]()
    if initiallyReady then readiness.success(())
    var registrations = Vector.empty[(String, Seq[String])]
    var closes = 0
    var registrationFailure = Option.empty[Throwable]
    var runFailure = Option.empty[Throwable]
    var closeFailure = Option.empty[Throwable]
    var requests = Vector.empty[(String, PythonRunConfig, Promise[PythonRunReport])]
    private var signals = Map.empty[Int, Promise[Unit]]

    def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
      registrations :+= ((moduleName, methodNames))
      registrationFailure.foreach(error => throw error)
      readiness.future

    def run(code: String, config: PythonRunConfig): Future[PythonRunReport] =
      runFailure.foreach(error => throw error)
      val index = requests.size
      val result = Promise[PythonRunReport]()
      requests :+= ((code, config, result))
      signals.get(index).foreach(_.trySuccess(()))
      result.future

    def started(index: Int = 0): Future[Unit] =
      if requests.size > index then Future.successful(())
      else
        val result = signals.getOrElse(index, Promise[Unit]())
        signals += index -> result
        result.future

    def complete(index: Int = 0, report: PythonRunReport = pythonReport()): Unit =
      requests(index)._3.success(report)

    override def close(): Unit =
      closes += 1
      closeFailure.foreach(error => throw error)

  test("Python execution allocates lazily and reuses only its own ready transport") {
    val executor = new ControlledPythonExecutor(initiallyReady = true)
    var allocations = 0
    val runner = new PyodideTurtleCommandRunner(() => { allocations += 1; executor })
    runner.close()
    runner.close()
    assertEquals(allocations, 0)
    val first = runner.execute("forward(10)")
    for
      _ <- executor.started()
      _ = executor.complete()
      actual <- first
      _ = assertEquals(actual, List(TurtleCommand[Double]("forward", List(10.0))))
      second = runner.execute("forward(20)")
      _ <- executor.started(1)
      _ = executor.complete(1, pythonReport(20))
      again <- second
    yield
      assertEquals(again, List(TurtleCommand[Double]("forward", List(20.0))))
      assertEquals(allocations, 1)
      assertEquals(executor.registrations.size, 2)
      assert(executor.registrations.forall((module, methods) => module == "turtle" && methods.contains("forward")))
      assertEquals(executor.requests.map(_._1), Vector("from turtle import *\nforward(10)", "from turtle import *\nforward(20)"))
      assert(executor.requests.forall(_._2.resetGlobals))
      runner.close()
      runner.close()
      assertEquals(executor.closes, 1)
  }

  test("Python execution refuses overlap without disturbing the active request") {
    val executor = new ControlledPythonExecutor
    val runner = new PyodideTurtleCommandRunner(() => executor)
    val first = runner.execute("forward(10)")
    for
      _ <- failedWith(runner.execute("forward(99)"))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ = executor.readiness.success(())
      _ <- executor.started()
      _ <- failedWith(runner.execute("forward(99)"))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ = assertEquals(executor.requests.size, 1)
      _ = assertEquals(executor.closes, 0)
      _ = executor.complete()
      commands <- first
    yield
      assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
      runner.close()
  }

  test("Python startup cancellation settles the request and ignores late readiness") {
    val old = new ControlledPythonExecutor
    val fresh = new ControlledPythonExecutor(initiallyReady = true)
    var allocations = 0
    val runner = new PyodideTurtleCommandRunner(() => { allocations += 1; if allocations == 1 then old else fresh })
    val first = runner.execute("forward(99)")
    runner.close()
    runner.close()
    val second = runner.execute("forward(10)")
    for
      _ <- failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
      _ <- fresh.started()
      _ = old.readiness.success(())
      _ <- delayed(0)
      _ = assertEquals(old.requests.size, 0)
      _ = assertEquals(old.closes, 1)
      _ = assert(!second.isCompleted)
      _ = fresh.complete()
      commands <- second
    yield
      assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
      assertEquals(allocations, 2)
      runner.close()
      assertEquals(fresh.closes, 1)
  }

  test("Python execution cancellation ignores an old result and restarts with a fresh transport") {
    val old = new ControlledPythonExecutor(initiallyReady = true)
    val fresh = new ControlledPythonExecutor(initiallyReady = true)
    var allocations = 0
    val runner = new PyodideTurtleCommandRunner(() => { allocations += 1; if allocations == 1 then old else fresh })
    val first = runner.execute("forward(99)")
    for
      _ <- old.started()
      _ = runner.close()
      _ <- failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
      second = runner.execute("forward(10)")
      _ <- fresh.started()
      _ = old.complete(report = pythonReport(99))
      _ <- delayed(0)
      _ = assert(!second.isCompleted)
      _ = assertEquals(old.closes, 1)
      _ = assertEquals(fresh.closes, 0)
      _ = fresh.complete()
      commands <- second
    yield
      assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
      assertEquals(allocations, 2)
      runner.close()
  }

  test("Python startup and execution timeouts discard their transport before retry") {
    List(false, true).foldLeft(Future.successful(())) { (previous, execution) =>
      previous.flatMap { _ =>
        val old = new ControlledPythonExecutor(initiallyReady = execution)
        val fresh = new ControlledPythonExecutor(initiallyReady = true)
        var allocations = 0
        val runner = new PyodideTurtleCommandRunner(() => { allocations += 1; if allocations == 1 then old else fresh },
          startupTimeoutMs = if execution then 1000 else 20, executionTimeoutMs = if execution then 20 else 1000)
        val first = runner.execute("forward(99)")
        for
          _ <- failedWith(first)(error => assert(error.isInstanceOf[TimeoutException]))
          _ = assertEquals(old.closes, 1)
          second = runner.execute("forward(10)")
          _ <- fresh.started()
          _ = if execution then old.complete(report = pythonReport(99)) else old.readiness.success(())
          _ <- delayed(0)
          _ = assert(!second.isCompleted)
          _ = assertEquals(old.requests.size, if execution then 1 else 0)
          _ = fresh.complete()
          commands <- second
        yield
          assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
          assertEquals(allocations, 2)
          runner.close()
      }
    }
  }

  test("Python synchronous factory, registration, run and cleanup failures permit retry") {
    List("factory", "registration", "run", "close").foldLeft(Future.successful(())) { (previous, stage) =>
      previous.flatMap { _ =>
        val error = IllegalStateException(s"$stage unavailable")
        val old = new ControlledPythonExecutor(initiallyReady = true)
        val fresh = new ControlledPythonExecutor(initiallyReady = true)
        if stage == "registration" then old.registrationFailure = Some(error)
        if stage == "run" then old.runFailure = Some(error)
        if stage == "close" then old.closeFailure = Some(error)
        var allocations = 0
        val runner = new PyodideTurtleCommandRunner(() => {
          allocations += 1
          if allocations == 1 && stage == "factory" then throw error
          if allocations == 1 then old else fresh
        })
        val first = runner.execute("forward(99)")
        val settled = if stage == "close" then old.started().flatMap { _ =>
          runner.close()
          failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
        } else failedWith(first)(actual => assert(actual eq error))
        for
          _ <- settled
          _ = assertEquals(old.closes, if stage == "factory" then 0 else 1)
          second = runner.execute("forward(10)")
          _ <- fresh.started()
          _ = fresh.complete()
          commands <- second
        yield
          assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
          assertEquals(allocations, 2)
          runner.close()
      }
    }
  }

  test("Python runners do not cancel or share another editor's requests") {
    val firstExecutor = new ControlledPythonExecutor(initiallyReady = true)
    val secondExecutor = new ControlledPythonExecutor(initiallyReady = true)
    val firstRunner = new PyodideTurtleCommandRunner(() => firstExecutor)
    val secondRunner = new PyodideTurtleCommandRunner(() => secondExecutor)
    val first = firstRunner.execute("forward(99)")
    val second = secondRunner.execute("forward(20)")
    for
      _ <- firstExecutor.started()
      _ <- secondExecutor.started()
      _ = firstRunner.close()
      _ <- failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
      _ = assert(!second.isCompleted)
      _ = assertEquals(secondExecutor.closes, 0)
      _ = firstExecutor.complete(report = pythonReport(99))
      _ = secondExecutor.complete(report = pythonReport(20))
      commands <- second
    yield
      assertEquals(commands, List(TurtleCommand[Double]("forward", List(20.0))))
      secondRunner.close()
      assertEquals(firstExecutor.closes, 1)
      assertEquals(secondExecutor.closes, 1)
  }

  test("Python malformed callbacks and oversized reports fail closed and discard the transport") {
    val invalid = List(
      pythonReport(Double.NaN), pythonReport(Double.PositiveInfinity), pythonReport(Double.NegativeInfinity),
      pythonReport().copy(callbackOps = Vector(CallbackOp("turtle", "unsupported", Vector.empty))),
      pythonReport().copy(callbackOps = Vector(CallbackOp("turtle", "forward", Vector[js.Any](js.Dynamic.literal(value = 1))))),
      pythonReport().copy(callbackOps = Vector.fill(10001)(CallbackOp("turtle", "forward", Vector[js.Any](1)))))
    invalid.foldLeft(Future.successful(())) { (previous, report) =>
      previous.flatMap { _ =>
        val old = new ControlledPythonExecutor(initiallyReady = true)
        val fresh = new ControlledPythonExecutor(initiallyReady = true)
        var allocations = 0
        val runner = new PyodideTurtleCommandRunner(() => { allocations += 1; if allocations == 1 then old else fresh })
        val first = runner.execute("forward(99)")
        for
          _ <- old.started()
          _ = old.complete(report = report)
          _ <- failedWith(first)(error => assert(error.isInstanceOf[IllegalArgumentException]))
          _ = assertEquals(old.closes, 1)
          second = runner.execute("forward(10)")
          _ <- fresh.started()
          _ = fresh.complete()
          commands <- second
        yield
          assertEquals(commands, List(TurtleCommand[Double]("forward", List(10.0))))
          runner.close()
      }
    }
  }

  private class ControlledJavaWorker(initiallyReady: Boolean = false) extends JavaTurtlePythonWorker:
    val readiness = Promise[Unit]()
    if initiallyReady then readiness.success(())
    var readyCalls = 0
    var terminations = 0
    var readyFailure = Option.empty[Throwable]
    var runFailure = Option.empty[Throwable]
    var terminationFailure = Option.empty[Throwable]
    var requests = Vector.empty[(String, PythonRunConfig, Promise[PythonRunReport])]
    private var signals = Map.empty[Int, Promise[Unit]]

    def ready(): Future[Unit] =
      readyCalls += 1
      readyFailure.foreach(error => throw error)
      readiness.future

    def run(code: String, config: PythonRunConfig): Future[PythonRunReport] =
      runFailure.foreach(error => throw error)
      val index = requests.size
      val result = Promise[PythonRunReport]()
      requests :+= ((code, config, result))
      signals.get(index).foreach(_.trySuccess(()))
      result.future

    def started(index: Int = 0): Future[Unit] =
      if requests.size > index then Future.successful(())
      else
        val result = signals.getOrElse(index, Promise[Unit]())
        signals += index -> result
        result.future

    def complete(index: Int = 0, report: PythonRunReport = javaReport()): Unit =
      requests(index)._3.success(report)

    def terminate(): Unit =
      terminations += 1
      terminationFailure.foreach(error => throw error)

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

  test("Java worker decoding retains status, command order and signed integer values") {
    val commands = """[["forward",-2147483648],["right",2147483647],["forward",0]]"""
    val expected = Vector(
      T.Command(R.TurtleCommand.Forward, Int.MinValue),
      T.Command(R.TurtleCommand.TurnRight, Int.MaxValue),
      T.Command(R.TurtleCommand.Forward, 0)
    )
    for status <- Vector("Completed", "LimitExceeded", "Cancelled") do
      val decoded = JavaTurtleCommandRunner.decode(javaReport(status, commands = commands, steps = "10"), T.Limits())
      assertEquals(decoded.commands, expected)
      assertEquals(decoded.steps, 10)
      assertEquals(decoded.status, status match
        case "Completed" => T.Status.Completed
        case "LimitExceeded" => T.Status.LimitExceeded
        case _ => T.Status.Cancelled)

    val division = JavaTurtleCommandRunner.decode(
      javaReport("Failed", "\"DivisionByZero\"", commands, "10"), T.Limits())
    assertEquals(division, T.Execution(T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)), expected, 10))
    assertEquals(JavaTurtleCommandRunner.decode(javaReport("Failed", "\"InvalidInvocation\"", steps = "0"), T.Limits()),
      T.Execution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0))
    assertEquals(JavaTurtleCommandRunner.decode(javaReport("Failed", "\"InvalidLimits\"", steps = "0"), T.Limits(maxSteps = 0)),
      T.Execution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0))
    assertEquals(JavaTurtleCommandRunner.decode(javaReport().copy(stdout = " \n" + javaReport().stdout + "\n "), T.Limits()),
      completedJava)
  }

  test("Java worker decoding rejects malformed envelopes and impossible execution results") {
    val valid = javaReport()
    val invalidEnvelopes = Vector(
      "", "not json", "null", "[]", "42", "true",
      valid.stdout + "\n" + valid.stdout,
      "log output\n" + valid.stdout,
      """{"status":"Completed","commands":[],"steps":2}""",
      """{"status":"Completed","problem":null,"commands":[],"steps":2,"extra":0}""",
      """{"status":null,"problem":null,"commands":[],"steps":2}""",
      """{"status":2,"problem":null,"commands":[],"steps":2}"""
    )
    val invalidResults = Vector(
      javaReport("Unknown"), javaReport("completed"), javaReport("Failed"),
      javaReport("Failed", "\"UnknownVariable\""), javaReport("Failed", "false"),
      javaReport("Completed", "\"DivisionByZero\""),
      javaReport("LimitExceeded", "\"InvalidInvocation\""),
      javaReport("Cancelled", "\"DivisionByZero\""),
      javaReport(steps = "-1"), javaReport(steps = "0"), javaReport(steps = "0.5"),
      javaReport(steps = "\"2\""), javaReport(steps = "true"), javaReport(steps = "null"),
      javaReport(steps = "2147483648"), javaReport(steps = "100001"),
      javaReport("Failed", "\"DivisionByZero\"", steps = "0"),
      javaReport("Failed", "\"NonFiniteCommand\"", steps = "0"),
      javaReport("Failed", "\"InvalidInvocation\"", steps = "1"),
      javaReport("Failed", "\"InvalidLimits\"", """[["forward",1]]""", "1")
    )
    val invalidCommands = Vector(
      "null", "{}", "true", "[null]", "[1]", "[[]]", "[[\"forward\"]]",
      "[[\"forward\",1,2]]", "[[null,1]]", "[[1,1]]", "[[true,1]]",
      "[[\"unknown\",1]]", "[[\"turnRight\",1]]", "[[\"Forward\",1]]",
      "[[\"forward\",\"1\"]]", "[[\"forward\",true]]", "[[\"forward\",null]]",
      "[[\"forward\",[]]]", "[[\"forward\",NaN]]", "[[\"forward\",Infinity]]",
      "[[\"right\",-Infinity]]", "[[\"forward\",1e309]]"
    )
    val reports = invalidEnvelopes.map(stdout => valid.copy(stdout = stdout)) ++ invalidResults ++
      invalidCommands.map(commands => javaReport(commands = commands)) ++ Vector(
        valid.copy(stderr = "warning"), valid.copy(stderr = " "),
        valid.copy(callbackOps = Vector(CallbackOp("other", "forward", Vector[js.Any](1)))),
        javaReport(commands = """[["forward",1],["right",90],["forward",2]]""", steps = "2")
      )
    reports.foreach { report =>
      intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(report, T.Limits()))
    }
    intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(javaReport(steps = "11"), T.Limits(maxSteps = 10)))
    intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(
      javaReport(commands = """[["forward",1]]"""), T.Limits(maxCommands = 0)))
    intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(valid.copy(stdout = " " * 1048577), T.Limits()))
    intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(valid, T.Limits(maxSteps = 0)))
    val tooManyCommands = Vector.fill(T.Limits.MaxCommands + 1)("""["forward",1]""").mkString("[", ",", "]")
    intercept[IllegalArgumentException](JavaTurtleCommandRunner.decode(
      javaReport(commands = tooManyCommands, steps = T.Limits.MaxSteps.toString), T.Limits()))
    val boundaryCommands = Vector.fill(T.Limits.MaxCommands)("""["forward",1]""").mkString("[", ",", "]")
    val boundary = JavaTurtleCommandRunner.decode(
      javaReport(commands = boundaryCommands, steps = T.Limits.MaxSteps.toString), T.Limits())
    assertEquals(boundary.commands.size, T.Limits.MaxCommands)
    assertEquals(boundary.steps, T.Limits.MaxSteps)
  }

  test("Java worker decoding retains finite doubles, signed zero and values beyond int32") {
    val commands = """[["forward",0.125],["right",-22.5],["forward",-0.0],["forward",2147483648],["forward",1.7976931348623157e308],["forward",5e-324]]"""
    val decoded = JavaTurtleCommandRunner.decode(javaReport(commands = commands, steps = "20"), T.Limits())
    assertEquals(decoded.commands.map(_.value).take(2), Vector(0.125, -22.5))
    assertEquals(1.0 / decoded.commands(2).value, Double.NegativeInfinity)
    assertEquals(decoded.commands(3).value, 2147483648.0)
    assertEquals(decoded.commands(4).value, Double.MaxValue)
    assertEquals(decoded.commands(5).value, java.lang.Double.MIN_VALUE)
  }

  test("Java worker nonfinite outcomes keep only their finite executed prefix") {
    val decoded = JavaTurtleCommandRunner.decode(
      javaReport("Failed", "\"NonFiniteCommand\"", """[["forward",0.25]]""", "7"), T.Limits())
    assertEquals(decoded.status, T.Status.Failed(T.Failure.NonFiniteCommand))
    assertEquals(decoded.commands, Vector(T.Command(R.TurtleCommand.Forward, 0.25)))
  }

  test("Java double Python literals preserve special values and signed zero") {
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.NaN), "float(\"nan\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.PositiveInfinity), "float(\"inf\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.NegativeInfinity), "float(\"-inf\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(-0.0), "-0.0")
    List(0.0, 1.0, 0.125, java.lang.Double.MIN_VALUE, Double.MaxValue).foreach { value =>
      assertEquals(JavaTurtlePythonExport.doubleLiteral(value).toDouble, value)
    }
  }

  test("Java worker invocation preserves fractional arguments and separate integer tags") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    val runner = new JavaTurtleCommandRunner(() => worker)
    val method = javaDoubleFixture.root.methods.find(_.binding.originalName == "draw").get.binding.id
    val result = runner.invoke(javaDoubleFixture, method, Vector(E.Value.DoubleValue(0.125), E.Value.IntValue(2)))
    for
      _ <- worker.started()
      _ = worker.complete(report = javaReport(commands = """[["forward",0.125],["right",22.5]]""", steps = "10"))
      actual <- result
    yield
      assert(worker.requests.head._1.contains(s"method=${method.index}, arguments=[0.125, 2]"))
      assertEquals(actual.commands.map(_.value), Vector(0.125, 22.5))
      runner.close()
  }

  test("Java worker widens integer arguments only for double parameters") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    val runner = new JavaTurtleCommandRunner(() => worker)
    val method = javaDoubleFixture.root.methods.find(_.binding.originalName == "draw").get.binding.id
    val result = runner.invoke(javaDoubleFixture, method, Vector(E.Value.IntValue(5), E.Value.IntValue(2)))
    for
      _ <- worker.started()
      _ = worker.complete(report = javaReport(commands = """[["forward",5]]""", steps = "7"))
      actual <- result
    yield
      assert(worker.requests.head._1.contains(s"method=${method.index}, arguments=[5, 2]"))
      assertEquals(actual.commands, Vector(T.Command(R.TurtleCommand.Forward, 5.0)))
      runner.close()
  }

  test("Java worker startup does not consume the execution deadline") {
    val worker = new ControlledJavaWorker
    val runner = new JavaTurtleCommandRunner(() => worker, startupTimeoutMs = 1000, executionTimeoutMs = 40)
    val result = runner.run(javaFixture)
    for
      _ <- delayed(80)
      _ = assert(!result.isCompleted)
      _ = assertEquals(worker.requests.size, 0)
      _ = worker.readiness.success(())
      _ <- worker.started()
      _ <- failedWith(runner.run(javaFixture))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ = worker.complete()
      actual <- result
    yield
      assertEquals(actual, completedJava)
      assertEquals(worker.readyCalls, 1)
      assertEquals(worker.terminations, 0)
      runner.close()
  }

  test("Java worker uses a fresh Python namespace while reusing a ready worker") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => { allocations += 1; worker })
    val first = runner.run(javaFixture)
    for
      _ <- worker.started()
      _ = worker.complete()
      actual <- first
      _ = assertEquals(actual, completedJava)
      second = runner.run(javaFixture)
      _ <- worker.started(1)
      _ = worker.complete(1)
      again <- second
    yield
      assertEquals(again, completedJava)
      assertEquals(allocations, 1)
      assertEquals(worker.readyCalls, 1)
      assertEquals(worker.requests(0)._1, worker.requests(1)._1)
      worker.requests.foreach { (code, config, _) =>
        assert(code.startsWith("import json as _java_json\n_java_namespace = {}\nexec("), clue = code)
        assert(code.contains(", _java_namespace, _java_namespace)"), clue = code)
        assert(code.contains("_java_namespace[\"java_turtle_run\"](method=None, arguments=[]"), clue = code)
        assert(!config.resetGlobals)
        assert(config.captureStdout)
        assert(config.captureStderr)
        assertEquals(config.context.size, 0)
      }
      assert(!runner.cancel())
      runner.close()
      runner.close()
      assertEquals(worker.terminations, 1)
  }

  test("Java worker invocation preserves method IDs, typed arguments and all four budgets") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    val runner = new JavaTurtleCommandRunner(() => worker)
    val method = javaFixture.root.methods.find(_.binding.originalName == "draw").get.binding.id
    val limits = T.Limits(maxSteps = 63, maxCommands = 3, maxCallDepth = 4, maxBlockDepth = 5)
    val result = runner.invoke(javaFixture, method, Vector(E.Value.IntValue(Int.MinValue), E.Value.BooleanValue(false)), limits)
    for
      _ <- worker.started()
      _ = worker.complete(report = javaReport(commands = """[["forward",-2147483648]]""", steps = "7"))
      actual <- result
    yield
      val code = worker.requests.head._1
      assert(code.contains(s"method=${method.index}, arguments=[-2147483648, False]"), clue = code)
      assert(code.contains("max_steps=63, max_commands=3, max_call_depth=4, max_block_depth=5"), clue = code)
      assertEquals(actual.commands, Vector(T.Command(R.TurtleCommand.Forward, Int.MinValue)))
      assertEquals(actual.steps, 7)
      runner.close()
  }

  test("Java worker rejects invalid limits and invocations without allocating a worker") {
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => { allocations += 1; new ControlledJavaWorker })
    val limits = Vector(
      T.Limits(maxSteps = 0), T.Limits(maxSteps = T.Limits.MaxSteps + 1),
      T.Limits(maxCommands = -1), T.Limits(maxCommands = T.Limits.MaxCommands + 1),
      T.Limits(maxCallDepth = 0), T.Limits(maxCallDepth = T.Limits.MaxCallDepth + 1),
      T.Limits(maxBlockDepth = 0), T.Limits(maxBlockDepth = T.Limits.MaxBlockDepth + 1)
    )
    val draw = javaFixture.root.methods.find(_.binding.originalName == "draw").get.binding.id
    val requests = limits.map(runner.run(javaFixture, _)) ++ Vector(
      runner.invoke(javaFixture, R.MethodId(-1), Vector.empty, T.Limits()),
      runner.invoke(javaFixture, javaFixture.root.entryPoint.binding.id, Vector.empty, T.Limits()),
      runner.invoke(javaFixture, draw, Vector.empty, T.Limits()),
      runner.invoke(javaFixture, draw, Vector(E.Value.DoubleValue(1.0), E.Value.BooleanValue(true)), T.Limits()),
      runner.invoke(javaFixture, draw, Vector(E.Value.BooleanValue(true), E.Value.IntValue(1)), T.Limits())
    )
    Future.sequence(requests).map { results =>
      results.take(limits.size).foreach(result => assertEquals(result,
        T.Execution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0)))
      results.drop(limits.size).foreach(result => assertEquals(result,
        T.Execution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0)))
      assertEquals(allocations, 0)
      assert(!runner.cancel())
      runner.close()
    }
  }

  test("Java worker refuses overlapping requests without disturbing the active run") {
    val worker = new ControlledJavaWorker
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => { allocations += 1; worker })
    val first = runner.run(javaFixture)
    for
      _ <- failedWith(runner.run(javaFixture))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ <- failedWith(runner.invoke(javaFixture, R.MethodId(-1), Vector.empty, T.Limits()))(
        error => assert(error.isInstanceOf[IllegalStateException]))
      _ = assert(!first.isCompleted)
      _ = assertEquals(allocations, 1)
      _ = worker.readiness.success(())
      _ <- worker.started()
      _ = worker.complete()
      actual <- first
    yield
      assertEquals(actual, completedJava)
      assertEquals(worker.requests.size, 1)
      assertEquals(worker.terminations, 0)
      runner.close()
  }

  test("Java worker cancellation during startup settles the run and ignores late readiness") {
    val old = new ControlledJavaWorker
    val replacement = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => {
      allocations += 1
      if allocations == 1 then old else replacement
    })
    val first = runner.run(javaFixture)
    assert(runner.cancel())
    assert(!runner.cancel())
    val second = runner.run(javaFixture)
    for
      _ <- failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
      _ <- replacement.started()
      _ = old.readiness.success(())
      _ <- delayed(0)
      _ = assertEquals(old.requests.size, 0)
      _ = assertEquals(old.terminations, 1)
      _ = assertEquals(replacement.terminations, 0)
      _ = assert(!second.isCompleted)
      _ = replacement.complete()
      actual <- second
    yield
      assertEquals(actual, completedJava)
      assertEquals(allocations, 2)
      runner.close()
  }

  test("Java worker cancellation during execution ignores late success and permits restart") {
    val old = new ControlledJavaWorker(initiallyReady = true)
    val replacement = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => {
      allocations += 1
      if allocations == 1 then old else replacement
    })
    val first = runner.run(javaFixture)
    for
      _ <- old.started()
      _ = assert(runner.cancel())
      _ <- failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
      second = runner.run(javaFixture)
      _ <- replacement.started()
      _ = old.complete(report = javaReport(commands = """[["forward",99]]""", steps = "5"))
      _ <- delayed(0)
      _ = assert(!second.isCompleted)
      _ = assertEquals(old.terminations, 1)
      _ = assertEquals(replacement.terminations, 0)
      _ = replacement.complete()
      actual <- second
    yield
      assertEquals(actual, completedJava)
      runner.close()
  }

  test("Java worker startup timeout discards the worker and ignores late failure") {
    val old = new ControlledJavaWorker
    val replacement = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => {
      allocations += 1
      if allocations == 1 then old else replacement
    }, startupTimeoutMs = 20)
    val first = runner.run(javaFixture)
    for
      _ <- failedWith(first)(error => assert(error.isInstanceOf[TimeoutException]))
      _ = assertEquals(old.terminations, 1)
      second = runner.run(javaFixture)
      _ <- replacement.started()
      _ = old.readiness.failure(new IllegalStateException("late startup failure"))
      _ <- delayed(0)
      _ = assertEquals(old.requests.size, 0)
      _ = assertEquals(replacement.terminations, 0)
      _ = replacement.complete()
      actual <- second
    yield
      assertEquals(actual, completedJava)
      runner.close()
  }

  test("Java worker execution timeout discards the worker and ignores late failure") {
    val old = new ControlledJavaWorker(initiallyReady = true)
    val replacement = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => {
      allocations += 1
      if allocations == 1 then old else replacement
    }, executionTimeoutMs = 20)
    val first = runner.run(javaFixture)
    for
      _ <- old.started()
      _ <- failedWith(first)(error => assert(error.isInstanceOf[TimeoutException]))
      _ = assertEquals(old.terminations, 1)
      second = runner.run(javaFixture)
      _ <- replacement.started()
      _ = old.requests.head._3.failure(new IllegalStateException("late execution failure"))
      _ <- delayed(0)
      _ = assertEquals(replacement.terminations, 0)
      _ = assert(!second.isCompleted)
      _ = replacement.complete()
      actual <- second
    yield
      assertEquals(actual, completedJava)
      runner.close()
  }

  test("Java worker startup failure permits a new worker") {
    val old = new ControlledJavaWorker
    val replacement = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => {
      allocations += 1
      if allocations == 1 then old else replacement
    })
    val first = runner.run(javaFixture)
    val failure = new IllegalStateException("startup failed")
    old.readiness.failure(failure)
    for
      _ <- failedWith(first)(error => assert(error eq failure))
      _ = assertEquals(old.terminations, 1)
      second = runner.run(javaFixture)
      _ <- replacement.started()
      _ = replacement.complete()
      actual <- second
    yield
      assertEquals(actual, completedJava)
      runner.close()
  }

  test("Java worker execution and protocol failures discard contaminated workers") {
    val failures = Vector[Either[Throwable, PythonRunReport]](
      Left(new IllegalStateException("execution failed")),
      Right(javaReport().copy(stdout = "not json")),
      Right(javaReport().copy(stderr = "unexpected output")),
      Right(javaReport().copy(callbackOps = Vector(CallbackOp("turtle", "forward", Vector[js.Any](1)))))
    )
    failures.foldLeft(Future.successful(())) { (previous, failure) =>
      previous.flatMap { _ =>
        val old = new ControlledJavaWorker(initiallyReady = true)
        val replacement = new ControlledJavaWorker(initiallyReady = true)
        var allocations = 0
        val runner = new JavaTurtleCommandRunner(() => {
          allocations += 1
          if allocations == 1 then old else replacement
        })
        val first = runner.run(javaFixture)
        for
          _ <- old.started()
          _ = failure match
            case Left(error) => old.requests.head._3.failure(error)
            case Right(report) => old.complete(report = report)
          _ <- failedWith(first) { error => failure match
            case Left(expected) => assert(error eq expected)
            case Right(_) => assert(error.isInstanceOf[IllegalArgumentException])
          }
          _ = assertEquals(old.terminations, 1)
          second = runner.run(javaFixture)
          _ <- replacement.started()
          _ = replacement.complete()
          actual <- second
        yield
          assertEquals(actual, completedJava)
          assertEquals(allocations, 2)
          runner.close()
      }
    }
  }

  test("Java worker synchronous transport and cleanup failures cannot leave pending runs") {
    Vector("factory", "ready", "run", "terminate").foldLeft(Future.successful(())) { (previous, stage) =>
      previous.flatMap { _ =>
        val failure = new IllegalStateException(s"$stage failed")
        val old = new ControlledJavaWorker(initiallyReady = true)
        val replacement = new ControlledJavaWorker(initiallyReady = true)
        if stage == "ready" then old.readyFailure = Some(failure)
        if stage == "run" then old.runFailure = Some(failure)
        if stage == "terminate" then old.terminationFailure = Some(failure)
        var allocations = 0
        val runner = new JavaTurtleCommandRunner(() => {
          allocations += 1
          if allocations == 1 && stage == "factory" then throw failure
          if allocations == 1 then old else replacement
        })
        val first = runner.run(javaFixture)
        val settled = if stage == "terminate" then
          old.started().flatMap { _ =>
            assert(runner.cancel())
            failedWith(first)(error => assert(error.isInstanceOf[CancellationException]))
          }
        else failedWith(first)(error => assert(error eq failure))
        for
          _ <- settled
          _ = assert(!runner.cancel())
          _ = assertEquals(old.terminations, if stage == "factory" then 0 else 1)
          second = runner.run(javaFixture)
          _ <- replacement.started()
          _ = replacement.complete()
          actual <- second
        yield
          assertEquals(actual, completedJava)
          assertEquals(allocations, 2)
          runner.close()
      }
    }
  }

  test("Java execution outcomes retain the ready worker and the executed prefix") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => { allocations += 1; worker })
    val outcomes = Vector(
      javaReport("LimitExceeded", commands = """[["forward",10]]""", steps = "5"),
      javaReport("Cancelled", commands = """[["forward",10]]""", steps = "5"),
      javaReport("Failed", "\"DivisionByZero\"", """[["forward",10]]""", "5"),
      javaReport("Failed", "\"NonFiniteCommand\"", """[["forward",0.25]]""", "5"),
      javaReport()
    )
    outcomes.zipWithIndex.foldLeft(Future.successful(())) { case (previous, (report, index)) =>
      previous.flatMap { _ =>
        val result = runner.run(javaFixture)
        worker.started(index).flatMap { _ =>
          worker.complete(index, report)
          result.map(actual => assertEquals(actual, JavaTurtleCommandRunner.decode(report, T.Limits())))
        }
      }
    }.map { _ =>
      assertEquals(allocations, 1)
      assertEquals(worker.readyCalls, 1)
      assertEquals(worker.terminations, 0)
      runner.close()
    }
  }

  test("closing an active Java runner cancels its promise and terminates its own worker once") {
    val worker = new ControlledJavaWorker(initiallyReady = true)
    val runner = new JavaTurtleCommandRunner(() => worker)
    val result = runner.run(javaFixture)
    for
      _ <- worker.started()
      _ = runner.close()
      _ = runner.close()
      _ = assert(!runner.cancel())
      _ <- failedWith(result)(error => assert(error.isInstanceOf[CancellationException]))
      _ <- failedWith(runner.run(javaFixture))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ <- failedWith(runner.invoke(javaFixture, R.MethodId(-1), Vector.empty, T.Limits()))(
        error => assert(error.isInstanceOf[IllegalStateException]))
      _ = worker.complete()
      _ <- delayed(0)
    yield assertEquals(worker.terminations, 1)
  }

  test("closing an unused Java runner never allocates a worker") {
    var allocations = 0
    val runner = new JavaTurtleCommandRunner(() => { allocations += 1; new ControlledJavaWorker })
    runner.close()
    runner.close()
    for
      _ <- failedWith(runner.run(javaFixture))(error => assert(error.isInstanceOf[IllegalStateException]))
      _ <- failedWith(runner.invoke(javaFixture, R.MethodId(-1), Vector.empty, T.Limits()))(
        error => assert(error.isInstanceOf[IllegalStateException]))
    yield
      assert(!runner.cancel())
      assertEquals(allocations, 0)
  }
