package it.evadid.homepage.webElements.editor.code
import munit.FunSuite
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import java.util.concurrent.CancellationException
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class TurtleProgramExecutionSpec extends FunSuite {
  test("runtime providers dispatch and clean up independently of Eva navigation") {
    val source = ProgrammingStateJavaString("unsupported source that a provider owns")
    var offered = Vector.empty[String]
    var executions = 0
    var stopped = Vector.empty[String]
    var closed = Vector.empty[String]
    val first = new EvaEditorExtension {
      override def turtleCommands(value: ProgrammingState) = { offered :+= "first"; None }
      override def cancel(): Unit = stopped :+= "first"
      override def close(): Unit = closed :+= "first"
    }
    val chosen = new EvaEditorExtension {
      override def turtleCommands(value: ProgrammingState) = {
        offered :+= "chosen"
        Some(() => { executions += 1; Future.successful(mappedCommands()) })
      }
      override def cancel(): Unit = stopped :+= "chosen"
      override def close(): Unit = closed :+= "chosen"
    }
    val execution = new TurtleProgramExecution(List(first, chosen))
    execution.commandsFor(source).map { commands =>
      assertEquals(commands, mappedCommands())
      assertEquals(offered, Vector("first", "chosen"))
      assertEquals(executions, 1)
      execution.cancel()
      execution.close()
      assertEquals(stopped, Vector("first", "chosen"))
      assertEquals(closed, Vector("first", "chosen"))
    }
  }
  private val testingConfig = EvaEditorConfig.Default
  private class ExecutionFixture(val state: Var[ProgrammingState], val extension: Option[JavaTurtleEditorExtension]) {
    private val execution = new TurtleProgramExecution(extension.toList)
    def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] = execution.commandsFor(state.now())
    def currentState(): ProgrammingState = state.now()
    def onFullscreenClose(): Unit = execution.close()
  }
  private def editorFor(source: ProgrammingState): ExecutionFixture = new ExecutionFixture(Var(source), None)
  private def javaPlain(state: Var[ProgrammingState], config: EvaEditorConfig,
      onStateEdited: ProgrammingState => Unit = _ => (),
      runnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner): ExecutionFixture =
    new ExecutionFixture(state, Some(new JavaTurtleEditorExtension(state, runnerFactory)))
  private def javaExtension(fixture: ExecutionFixture): JavaTurtleEditorExtension = fixture.extension.get
  private def javaSource(distance: Int = 12): ProgrammingStateJavaString =
    ProgrammingStateJavaString(s"\n\tpublic class Drawing {\r\n  static void draw(int distance) {\r\n    Turtle.forward(distance);\r\n    Turtle.turnRight(-90);\r\n  }\r\n  public static void main(String[] args) { draw($distance); }\r\n}\r\n\t ")

  private def mappedCommands(distance: Double = 12.0): List[TurtleCommand[Double]] =
    List(TurtleCommand[Double]("forward", List(distance)), TurtleCommand[Double]("right", List(-90.0)))

  private def completedJava(distance: Int = 12): T.Execution =
    T.Execution(T.Status.Completed, Vector(
      T.Command(R.TurtleCommand.Forward, distance),
      T.Command(R.TurtleCommand.TurnRight, -90)
    ), 10)

  private class ControlledRunner extends JavaEditorSession.Runner {
    case class Request(program: P.Program, limits: T.Limits, result: Promise[T.Execution])
    var requests = Vector.empty[Request]
    var cancelCalls = 0
    var closeCalls = 0
    var onRun: () => Unit = () => ()

    override def run(program: P.Program, limits: T.Limits): Future[T.Execution] = {
      val result = Promise[T.Execution]()
      requests :+= Request(program, limits, result)
      onRun()
      result.future
    }

    override def cancel(): Boolean = {
      cancelCalls += 1
      requests.exists(request => !request.result.isCompleted)
    }

    override def close(): Unit = { closeCalls += 1 }

    def complete(index: Int = 0, execution: T.Execution = completedJava()): Unit =
      requests(index).result.success(execution)
  }

  private class ControlledRunnerFactory {
    var runners = Vector.empty[ControlledRunner]
    val create: () => JavaEditorSession.Runner = () => {
      val runner = new ControlledRunner
      runners :+= runner
      runner
    }
  }

  private def rejectsPartialJavaExecution(status: T.Status): Future[Unit] = {
    val source = javaSource()
    val stored = ProgrammingState.serializer.serialize(source)
    val factory = new ControlledRunnerFactory
    var published = List.empty[ProgrammingState]
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next, factory.create)
    val result = editor.getCurrentTurtleCommands()
    assertEquals(factory.runners.size, 1)
    factory.runners.head.complete(execution = completedJava().copy(status = status))
    result.failed.map { _ =>
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingState.serializer.serialize(editor.currentState()), stored)
      assertEquals(published, Nil)
    }
  }

  test("legacy Java snippets remain convertible without running a full Java class") {
    val source = ProgrammingStateJavaString("forward(12);")
    val editor = editorFor(source)
    assertEquals(source.toBeExpressionState.deriveTurtleCommands, List(TurtleCommand[Double]("forward", List(12.0))))
    assertEquals(editor.currentState(), source)
  }

  test("running an unsupported Java class fails without replacing its source") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic static void main(String[] args) { double side = 25; }\r\n}\r\n\t ")
    val editor = editorFor(source)
    editor.getCurrentTurtleCommands().failed.map { _ =>
      assertEquals(editor.currentState(), source)
    }
  }

  test("run uses a source restored while the editor is closed") {
    val editor = editorFor(ProgrammingStatePythonString("forward(12)"))
    val restored = ProgrammingStateJavaString("\npublic class Drawing {}\r\n\t ")
    editor.state.set(restored)
    editor.getCurrentTurtleCommands().failed.map { _ =>
      assertEquals(editor.currentState(), restored)
    }
  }

  test("runs a checked Java main and helper and maps signed turtle commands without changing the draft") {
    val source = javaSource(-12)
    val stored = ProgrammingState.serializer.serialize(source)
    val fingerprint = ProgrammingState.fingerprint(source)
    val factory = new ControlledRunnerFactory
    var published = List.empty[ProgrammingState]
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next, factory.create)
    val result = editor.getCurrentTurtleCommands()
    assertEquals(factory.runners.size, 1)
    val runner = factory.runners.head
    assertEquals(runner.requests.size, 1)
    val request = runner.requests.head
    assertEquals(request.program.root.entryPoint.binding.originalName, "main")
    assertEquals(request.program.root.methods.map(_.binding.originalName), Vector("draw", "main"))
    assertEquals(request.limits, T.Limits())
    val execution = T.runVm(request.program, request.limits)
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, completedJava(-12).commands)
    runner.complete(execution = execution)
    result.map { commands =>
      assertEquals(commands, mappedCommands(-12.0))
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingState.serializer.serialize(editor.currentState()), stored)
      assertEquals(ProgrammingState.fingerprint(editor.currentState()), fingerprint)
      assertEquals(published, Nil)
    }
  }

  test("unfinished classes and invalid Java main programs reject before creating a runner") {
    val sources = List(
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\t"),
      ProgrammingStateJavaString("class Drawing {}"),
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { float side = 25; } }"),
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(missing); } }")
    )
    Future.sequence(sources.map { source =>
      val factory = new ControlledRunnerFactory
      val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
        runnerFactory = factory.create)
      val stored = ProgrammingState.serializer.serialize(source)
      editor.getCurrentTurtleCommands().failed.map { _ =>
        assertEquals(factory.runners.size, 0)
        assertEquals(editor.currentState(), source)
        assertEquals(ProgrammingState.serializer.serialize(editor.currentState()), stored)
      }
    }).map(_ => ())
  }

  test("Java execution limits reject partial turtle commands") {
    rejectsPartialJavaExecution(T.Status.LimitExceeded)
  }

  test("Java evaluation failures reject partial turtle commands") {
    rejectsPartialJavaExecution(T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)))
  }

  test("cancelled Java execution reports reject partial turtle commands") {
    rejectsPartialJavaExecution(T.Status.Cancelled)
  }

  test("Java transport failures reject without changing the raw serialized source") {
    val source = javaSource()
    val stored = ProgrammingState.serializer.serialize(source)
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val error = new IllegalStateException("worker failed")
    val result = editor.getCurrentTurtleCommands()
    factory.runners.head.requests.head.result.failure(error)
    result.failed.map { actual =>
      assert(actual eq error)
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingState.serializer.serialize(editor.currentState()), stored)
    }
  }

  test("a second Java run rejects while the first run remains active") {
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](javaSource()), testingConfig,
      runnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    val second = editor.getCurrentTurtleCommands()
    second.failed.flatMap { error =>
      assert(error.isInstanceOf[IllegalStateException])
      assertEquals(factory.runners.size, 1)
      assertEquals(runner.requests.size, 1)
      assertEquals(runner.cancelCalls, 0)
      assertEquals(runner.closeCalls, 0)
      assert(!first.isCompleted)
      runner.complete()
      first.map(commands => assertEquals(commands, mappedCommands()))
    }
  }

  test("a reentrant Java run rejects without replacing the first execution") {
    val runner = new ControlledRunner
    val editor = javaPlain(Var[ProgrammingState](javaSource()), testingConfig,
      runnerFactory = () => runner)
    var reentrant = Option.empty[Future[List[TurtleCommand[Double]]]]
    runner.onRun = () => reentrant = Some(editor.getCurrentTurtleCommands())
    val first = editor.getCurrentTurtleCommands()
    assertEquals(runner.requests.size, 1)
    reentrant.get.failed.flatMap { error =>
      assert(error.isInstanceOf[IllegalStateException])
      assertEquals(runner.cancelCalls, 0)
      assertEquals(runner.closeCalls, 0)
      assert(!first.isCompleted)
      runner.complete()
      first.map(commands => assertEquals(commands, mappedCommands()))
    }
  }

  test("stopping Java execution cancels immediately and allows a fresh run") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    javaExtension(editor).stop()
    assertEquals(runner.cancelCalls, 1)
    assert(!runner.requests.head.result.isCompleted)
    first.failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      assertEquals(editor.currentState(), source)
      val restarted = editor.getCurrentTurtleCommands()
      assertEquals(factory.runners.size, 1)
      assertEquals(runner.requests.size, 2)
      runner.complete(0, completedJava(999))
      runner.requests.head.result.future.flatMap { _ =>
        assert(!restarted.isCompleted)
        runner.complete(1)
        restarted.map(commands => assertEquals(commands, mappedCommands()))
      }
    }
  }

  test("closing an unmounted editor cancels Java execution and restarts with a new runner") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val previous = factory.runners.head
    editor.onFullscreenClose()
    assertEquals(previous.cancelCalls, 1)
    assertEquals(previous.closeCalls, 1)
    assert(!previous.requests.head.result.isCompleted)
    first.failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      val restarted = editor.getCurrentTurtleCommands()
      assertEquals(factory.runners.size, 2)
      val current = factory.runners.last
      previous.complete(execution = completedJava(999))
      previous.requests.head.result.future.flatMap { _ =>
        assertEquals(current.cancelCalls, 0)
        assert(!restarted.isCompleted)
        current.complete()
        restarted.map { commands =>
          assertEquals(commands, mappedCommands())
          assertEquals(editor.currentState(), source)
        }
      }
    }
  }

  test("replacing a closed editor source immediately cancels Java execution and runs the restored source") {
    val initial = javaSource()
    val restored = javaSource(42)
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](initial), testingConfig,
      runnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    editor.state.set(restored)
    assertEquals(runner.cancelCalls, 1)
    assertEquals(runner.closeCalls, 1)
    assert(!runner.requests.head.result.isCompleted)
    first.failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      val restarted = editor.getCurrentTurtleCommands()
      assertEquals(factory.runners.size, 2)
      val current = factory.runners.last
      assertEquals(current.requests.size, 1)
      val execution = T.runVm(current.requests.head.program, current.requests.head.limits)
      assertEquals(execution.commands, completedJava(42).commands)
      runner.complete(0, completedJava(999))
      runner.requests.head.result.future.flatMap { _ =>
        assert(!restarted.isCompleted)
        assertEquals(current.closeCalls, 0)
        current.complete(execution = execution)
        restarted.map { commands =>
          assertEquals(commands, mappedCommands(42.0))
          assertEquals(editor.currentState(), restored)
        }
      }
    }
  }

  test("a closed editor source changing from A to B and back to A cannot revive the old Java execution") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    editor.state.set(javaSource(42))
    assertEquals(runner.cancelCalls, 1)
    assertEquals(runner.closeCalls, 1)
    editor.state.set(source)
    first.failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      assertEquals(editor.currentState(), source)
      val restarted = editor.getCurrentTurtleCommands()
      assertEquals(factory.runners.size, 2)
      val current = factory.runners.last
      assertEquals(current.requests.size, 1)
      runner.complete(0, completedJava(999))
      runner.requests.head.result.future.flatMap { _ =>
        assert(!restarted.isCompleted)
        assertEquals(runner.cancelCalls, 1)
        assertEquals(current.cancelCalls, 0)
        assertEquals(current.closeCalls, 0)
        current.complete()
        restarted.map(commands => assertEquals(commands, mappedCommands()))
      }
    }
  }

  test("a source change after the worker response still invalidates Java commands before delivery") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val result = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    val changed = runner.requests.head.result.future.map { _ =>
      editor.state.set(javaSource(42))
      editor.state.set(source)
    }
    runner.complete()
    changed.flatMap { _ =>
      result.failed.flatMap { error =>
        assert(error.isInstanceOf[CancellationException])
        assertEquals(editor.currentState(), source)
        val restarted = editor.getCurrentTurtleCommands()
        assertEquals(factory.runners.size, 2)
        assertEquals(runner.closeCalls, 1)
        val current = factory.runners.last
        assertEquals(current.requests.size, 1)
        current.complete()
        restarted.map(commands => assertEquals(commands, mappedCommands()))
      }
    }
  }

  test("restoring an identical Java source keeps its pending execution active") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
    val result = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    editor.state.set(source.copy())
    assertEquals(runner.cancelCalls, 0)
    assertEquals(runner.closeCalls, 0)
    runner.complete()
    result.map(commands => assertEquals(commands, mappedCommands()))
  }

  test("Java session release rejects a reentrant run until its old runner is closed") {
    val factory = new ControlledRunnerFactory
    val session = new JavaEditorSession(javaSource(), factory.create)
    val owner = new ManualOwner
    var reentrant = Option.empty[Future[T.Execution]]
    session.status.signal.changes.foreach {
      case JavaEditorSession.State.Stopped => reentrant = Some(session.run())
      case _ => ()
    }(using owner)
    val first = session.run()
    session.release()
    owner.killSubscriptions()
    assertEquals(factory.runners.head.cancelCalls, 1)
    assertEquals(factory.runners.head.closeCalls, 1)
    assertEquals(factory.runners.size, 1)
    for {
      cancelled <- first.failed
      _ = assert(cancelled.isInstanceOf[CancellationException])
      rejected <- reentrant.get.failed
      _ = assert(rejected.isInstanceOf[IllegalStateException])
      fresh = session.run()
      _ = assertEquals(factory.runners.size, 2)
      _ = factory.runners.last.complete()
      execution <- fresh
    } yield assertEquals(execution.status, T.Status.Completed)
  }

  test("Java runner factory failure rejects promptly and leaves the editor ready to retry") {
    val runner = new ControlledRunner
    var calls = 0
    val error = IllegalStateException("worker unavailable")
    val editor = javaPlain(Var[ProgrammingState](javaSource()), testingConfig,
      runnerFactory = () => { calls += 1; if calls == 1 then throw error else runner })
    editor.getCurrentTurtleCommands().failed.flatMap { failure =>
      assert(failure eq error)
      val retry = editor.getCurrentTurtleCommands()
      assertEquals(calls, 2)
      assertEquals(runner.requests.size, 1)
      runner.complete()
      retry.map(commands => assertEquals(commands, mappedCommands()))
    }
  }

  test("legacy Java fragments with class words in comments or strings run without a Java runner") {
    val sources = List(
      "forward(12);",
      "// class Fake {}\nforward(12);",
      "String note = \"class Fake { }\"; forward(12);"
    )
    Future.sequence(sources.map { code =>
      val source = ProgrammingStateJavaString(code)
      val factory = new ControlledRunnerFactory
      val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
        runnerFactory = factory.create)
      editor.getCurrentTurtleCommands().map { commands =>
        assertEquals(commands, List(TurtleCommand[Double]("forward", List(12.0))))
        assertEquals(factory.runners.size, 0)
        assertEquals(editor.currentState(), source)
      }
    }).map(_ => ())
  }
}
