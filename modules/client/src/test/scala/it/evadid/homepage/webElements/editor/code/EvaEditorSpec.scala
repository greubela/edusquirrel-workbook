package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorPlain, EvaEditorTurtle, EvaProgrammingTab}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import munit.FunSuite

import java.util.concurrent.CancellationException
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class EvaEditorSpec extends FunSuite {
  private val testingConfig = EvaEditorConfig(snapConfig = SnapCodeEditorConfig.Testing)

  private def squareDrawing(side: Double = 25, turn: Double = 90): List[TurtleCommand[Double]] =
    List.fill(4)(List(TurtleCommand[Double]("forward", List(side)), TurtleCommand[Double]("right", List(turn)))).flatten

  private class PanelFixture {
    val source = Var[ProgrammingState](ProgrammingStateJavaString("class Drawing {}"))
    var requests = Vector.empty[Promise[List[TurtleCommand[Double]]]]
    var stops = 0
    val panel = new JavaTurtleExecutionPanel(source,
      () => { val result = Promise[List[TurtleCommand[Double]]](); requests :+= result; result.future },
      () => stops += 1, Some(TurtleGraphic.TurtleGraphicProgram(squareDrawing())))
    panel.activate()
  }

  test("Java drawing comparison rejects wrong lengths, turns, empty drawings and extra strokes") {
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    assert(JavaTurtleExecutionPanel.compare(squareDrawing(), target))
    for commands <- List(squareDrawing(24), squareDrawing(26), squareDrawing(25, 89), squareDrawing(25, 45),
      Nil, squareDrawing() :+ TurtleCommand[Double]("backward", List(10.0))) do
      assert(!JavaTurtleExecutionPanel.compare(commands, target), clue = commands)
  }

  test("Java drawing comparison accepts split strokes and ignores duplicate tracing") {
    val split = List.fill(4)(List(TurtleCommand[Double]("forward", List(12.0)),
      TurtleCommand[Double]("forward", List(13.0)), TurtleCommand[Double]("right", List(90.0)))).flatten
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    assert(JavaTurtleExecutionPanel.compare(split, target))
    assert(JavaTurtleExecutionPanel.compare(squareDrawing() ++ squareDrawing(), target))
  }

  test("Java drawing comparison uses pen-up moves for disconnected targets without drawing their gaps") {
    import it.evadid.core.datastructures.geometry.Point
    val target = TurtleGraphic.TurtleLineBasedProgram(List(
      TurtleGraphic.Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      TurtleGraphic.Line(Point(20.0, 0.0), Point(30.0, 0.0))))
    assert(JavaTurtleExecutionPanel.compare(target.toTurtleProgram.toList, target))
    assert(!JavaTurtleExecutionPanel.compare(List(TurtleCommand[Double]("forward", List(30.0))), target))
  }

  test("Java drawing comparison bounds huge traces and rejects unsupported or non-finite commands") {
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    for commands <- List(List(TurtleCommand[Double]("forward", List(Int.MaxValue.toDouble))),
      List(TurtleCommand[Double]("forward", List(Double.PositiveInfinity))),
      List(TurtleCommand[Double]("forward", List(Double.NaN))),
      List(TurtleCommand[Double]("circle", List(25.0))), List.fill(100)(squareDrawing()).flatten) do
      intercept[IllegalArgumentException](JavaTurtleExecutionPanel.compare(commands, target))
    val empty = TurtleGraphic.TurtleGraphicProgram(Nil)
    assert(JavaTurtleExecutionPanel.compare(Nil, empty))
    assert(!JavaTurtleExecutionPanel.compare(squareDrawing(), empty))
  }

  test("Java execution panel publishes assessed drawing only after successful completion") {
    import JavaTurtleExecutionPanel.Status
    val fixture = new PanelFixture
    fixture.panel.run()
    fixture.panel.run()
    assertEquals(fixture.requests.size, 1)
    assertEquals(fixture.panel.status.now(), Status.Running)
    fixture.requests.head.success(squareDrawing())
    fixture.requests.head.future.map { _ => () }.flatMap { _ =>
      Future.unit.map { _ =>
        assertEquals(fixture.panel.status.now(), Status.Ready(squareDrawing(), Some(true)))
        fixture.panel.deactivate()
        assertEquals(fixture.panel.status.now(), Status.Idle)
      }
    }
  }

  test("Java execution panel stop ignores old results and permits a new run") {
    import JavaTurtleExecutionPanel.Status
    val fixture = new PanelFixture
    fixture.panel.run()
    fixture.panel.stop()
    fixture.panel.stop()
    assertEquals(fixture.stops, 1)
    assertEquals(fixture.panel.status.now(), Status.Stopped)
    fixture.panel.run()
    val newest = fixture.requests.last
    fixture.requests.head.success(squareDrawing())
    fixture.requests.head.future.flatMap { _ => Future.unit.map { _ =>
      assertEquals(fixture.panel.status.now(), Status.Running)
      newest.success(squareDrawing(24))
    }}.flatMap(_ => newest.future).flatMap(_ => Future.unit.map { _ =>
      assertEquals(fixture.panel.status.now(), Status.Ready(squareDrawing(24), Some(false)))
      fixture.panel.deactivate()
    })
  }

  test("Java execution panel invalidates A-to-B-to-A restores but ignores identical echoes") {
    import JavaTurtleExecutionPanel.Status
    val fixture = new PanelFixture
    val original = fixture.source.now()
    fixture.panel.run()
    fixture.source.set(original)
    assertEquals(fixture.panel.status.now(), Status.Running)
    assertEquals(fixture.stops, 0)
    fixture.source.set(ProgrammingStateJavaString("class Changed {}"))
    fixture.source.set(original)
    assertEquals(fixture.panel.status.now(), Status.Idle)
    assertEquals(fixture.stops, 1)
    fixture.requests.head.success(squareDrawing())
    fixture.requests.head.future.flatMap(_ => Future.unit.map { _ =>
      assertEquals(fixture.panel.status.now(), Status.Idle)
      fixture.panel.deactivate()
    })
  }

  test("Java execution panel unmount clears results and rejects late completion") {
    import JavaTurtleExecutionPanel.Status
    val fixture = new PanelFixture
    fixture.panel.run()
    fixture.panel.deactivate()
    assertEquals(fixture.stops, 1)
    fixture.requests.head.success(squareDrawing())
    fixture.requests.head.future.flatMap(_ => Future.unit.map { _ =>
      assertEquals(fixture.panel.status.now(), Status.Idle)
      fixture.panel.activate()
      fixture.panel.run()
      assertEquals(fixture.requests.size, 2)
      fixture.panel.deactivate()
    })
  }

  test("Java execution panel catches synchronous failure without fabricating a drawing") {
    import JavaTurtleExecutionPanel.Status
    val source = Var[ProgrammingState](ProgrammingStateJavaString("class Drawing {}"))
    val panel = new JavaTurtleExecutionPanel(source, () => throw IllegalStateException("unavailable"), () => ())
    panel.run()
    Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Failed("unavailable"))
      panel.deactivate()
    }
  }

  test("Java execution panel does not launch a run stopped by a status observer") {
    import JavaTurtleExecutionPanel.Status
    val fixture = new PanelFixture
    val owner = new ManualOwner
    fixture.panel.status.signal.changes.foreach {
      case Status.Running => fixture.panel.stop()
      case _ => ()
    }(using owner)
    fixture.panel.run()
    assertEquals(fixture.requests.size, 0)
    assertEquals(fixture.panel.status.now(), Status.Stopped)
    owner.killSubscriptions()
    fixture.panel.deactivate()
  }

  private def editorFor(source: ProgrammingState, config: EvaEditorConfig = testingConfig): EvaEditor =
    new EvaEditorPlain(Var[ProgrammingState](source), config)

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

  private class MethodRunner extends JavaEditorSession.Runner {
    case class Call(program: P.Program, method: R.MethodId, arguments: Vector[E.Value], limits: T.Limits)
    var calls = Vector.empty[Call]
    var mainCalls = 0
    var cancels = 0
    var closes = 0
    var onInvoke: () => Unit = () => ()
    var pending = Option.empty[Promise[T.Execution]]
    override def run(program: P.Program, limits: T.Limits): Future[T.Execution] = {
      mainCalls += 1
      Future.successful(T.runVm(program, limits))
    }
    override def invoke(program: P.Program, method: R.MethodId, arguments: Vector[E.Value], limits: T.Limits): Future[T.Execution] = {
      calls :+= Call(program, method, arguments, limits)
      onInvoke()
      pending.fold(Future.successful(T.invokeVm(program, method, arguments, limits)))(_.future)
    }
    override def cancel(): Boolean = { cancels += 1; true }
    override def close(): Unit = { closes += 1 }
  }

  private def squareSource(body: String, main: String = "square(25);"): ProgrammingStateJavaString =
    ProgrammingStateJavaString(s"\r\npublic class Drawing {\n  static void square(int side) { $body }\n  public static void main(String[] args) { $main }\n}\t ")

  private val squareBody = "for (int i = 0; i < 4; i += 1) { Turtle.forward(side); Turtle.turnRight(90); }"

  test("Java task invokes parameter values independently of main and compiles one shared program") {
    val source = squareSource(squareBody, "while (true) {}")
    val saved = ProgrammingExercise.StateSerializer.serialize(source)
    val runner = new MethodRunner
    val editor = EvaEditorPlain(Var[ProgrammingState](source), testingConfig, javaRunnerFactory = () => runner)
    editor.checkJavaTask(JavaTurtleTask.squarePilot).map { drawings =>
      assertEquals(runner.mainCalls, 0)
      assertEquals(runner.calls.map(_.arguments), Vector(Vector(E.Value.IntValue(25)), Vector(E.Value.IntValue(40)), Vector(E.Value.IntValue(0))))
      assert(runner.calls.forall(_.program eq runner.calls.head.program))
      assert(runner.calls.forall(_.program.root.methods.find(_.binding.id == runner.calls.head.method).exists(_.binding.originalName == "square")))
      assertEquals(drawings.size, 3)
      assert(drawings.zip(JavaTurtleTask.squarePilot.cases).forall { (drawing, example) => JavaTurtleExecutionPanel.compare(drawing, example.expectedShape) })
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), saved)
      editor.onFullscreenClose()
    }
  }

  test("Java task rejects hardcoded side lengths and wrong zero behavior geometrically") {
    val bodies = List(squareBody.replace("forward(side)", "forward(25)"),
      "if (side == 0) { Turtle.forward(1); } else { " + squareBody + " }")
    Future.sequence(bodies.map { body =>
      val runner = new MethodRunner
      val editor = EvaEditorPlain(Var[ProgrammingState](squareSource(body)), testingConfig, javaRunnerFactory = () => runner)
      editor.checkJavaTask(JavaTurtleTask.squarePilot).map { drawings =>
        val matches = drawings.zip(JavaTurtleTask.squarePilot.cases).map { (drawing, example) => JavaTurtleExecutionPanel.compare(drawing, example.expectedShape) }
        assert(matches.head)
        assert(!matches.forall(identity))
        assert(!matches.last)
        editor.onFullscreenClose()
      }
    }).map(_ => ())
  }

  test("Java task detects missing methods, signature mismatches and incomplete definitions before invocation") {
    val sources = List(squareSource(squareBody).copy(code = squareSource(squareBody).code.replace("square", "other")),
      squareSource(squareBody).copy(code = squareSource(squareBody).code.replace("int side", "boolean side").replace("forward(side)", "forward(25)").replace("square(25)", "square(true)")))
    Future.sequence(sources.map { source =>
      val runner = new MethodRunner
      val editor = EvaEditorPlain(Var[ProgrammingState](source), testingConfig, javaRunnerFactory = () => runner)
      editor.checkJavaTask(JavaTurtleTask.squarePilot).failed.map { _ =>
        assertEquals(runner.calls.size, 0)
        assertEquals(runner.mainCalls, 0)
        editor.onFullscreenClose()
      }
    }).flatMap { _ =>
      val runner = new MethodRunner
      val session = new JavaEditorSession(squareSource(squareBody), () => runner)
      session.checkTask(JavaTurtleTask.squarePilot.copy(cases = Nil)).failed.map { _ =>
        assertEquals(runner.calls.size, 0)
        session.release()
      }
    }
  }

  test("Java task stops after runtime failure without returning a partial successful assessment") {
    val runner = new MethodRunner
    val source = squareSource("Turtle.forward(side); int zero = 0; int value = 1 / zero;")
    val editor = EvaEditorPlain(Var[ProgrammingState](source), testingConfig, javaRunnerFactory = () => runner)
    editor.checkJavaTask(JavaTurtleTask.squarePilot).failed.map { error =>
      assert(error.getMessage.contains("divide by zero"))
      assertEquals(runner.calls.size, 1)
      editor.onFullscreenClose()
    }
  }

  test("Java task cancellation prevents later cases and preserves the source") {
    val runner = new MethodRunner
    val gate = Promise[T.Execution]()
    runner.pending = Some(gate)
    val source = squareSource(squareBody)
    val session = new JavaEditorSession(source, () => runner)
    val pending = session.checkTask(JavaTurtleTask.squarePilot)
    Future.unit.flatMap { _ =>
      assertEquals(runner.calls.size, 1)
      session.stop()
      gate.success(T.invokeVm(runner.calls.head.program, runner.calls.head.method, runner.calls.head.arguments))
      pending.failed.flatMap { error =>
        assert(error.isInstanceOf[CancellationException])
        gate.future.flatMap(_ => Future.unit.map { _ =>
          assertEquals(runner.calls.size, 1)
          assertEquals(session.source, source)
          session.release()
        })
      }
    }
  }

  test("Java task source replacement cancels the whole assessment without starting later values") {
    val runner = new MethodRunner
    val source = squareSource(squareBody)
    val editor = EvaEditorPlain(Var[ProgrammingState](source), testingConfig, javaRunnerFactory = () => runner)
    runner.onInvoke = () => { editor.state.set(squareSource("")); editor.state.set(source) }
    editor.checkJavaTask(JavaTurtleTask.squarePilot).failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      Future.unit.map { _ =>
        assertEquals(runner.calls.size, 1)
        assertEquals(runner.closes, 1)
        assertEquals(editor.currentState(), source)
      }
    }
  }

  test("Java task panel rejects an incomplete case result and never shows overall success") {
    import JavaTurtleExecutionPanel.Status
    val source = Var[ProgrammingState](squareSource(squareBody))
    val result = Promise[Vector[List[TurtleCommand[Double]]]]()
    val panel = new JavaTurtleExecutionPanel(source, () => Future.successful(Nil), () => (),
      Some(JavaTurtleTask.squarePilot.cases.head.expectedShape), Some(JavaTurtleTask.squarePilot -> (() => result.future)))
    panel.activate()
    panel.checkTask()
    result.success(Vector(squareDrawing()))
    result.future.flatMap(_ => Future.unit.map { _ =>
      assert(panel.status.now().isInstanceOf[Status.Failed])
      panel.deactivate()
    })
  }

  private def rejectsPartialJavaExecution(status: T.Status): Future[Unit] = {
    val source = javaSource()
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    val factory = new ControlledRunnerFactory
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next, factory.create)
    val result = editor.getCurrentTurtleCommands()
    assertEquals(factory.runners.size, 1)
    factory.runners.head.complete(execution = completedJava().copy(status = status))
    result.failed.map { _ =>
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
      assertEquals(published, Nil)
    }
  }

  test("tab synchronization compares the converted state and ignores identical restores") {
    val snap = ProgrammingStateSnapXml.mini
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Python, Var[ProgrammingState](snap), _ => ())
    val owner = new ManualOwner
    var changes = 0
    try {
      tab.associatedVar.signal.changes.foreach(_ => changes += 1)(using owner)
      tab.setStateTo(snap)
      tab.setStateTo(snap)
      assertEquals(changes, 0)
      val changed = ProgrammingStatePythonString("forward(25)")
      tab.setStateTo(changed)
      tab.setStateTo(changed)
      assertEquals(changes, 1)
    } finally owner.killSubscriptions()
  }

  test("Snap tab forwards edits to the parent editor") {
    val initial = ProgrammingStateSnapXml.mini
    var published: Option[ProgrammingState] = None
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](initial), next => published = Some(next))
    val next = ProgrammingStateSnapXml.empty
    tab.editorElement.asInstanceOf[SnapEditor.SnapCodeEditor].onStateEdited(next)
    assertEquals(published, Some(next))
  }

  test("configured language creates a matching tab with the current editor state") {
    val python = ProgrammingStatePythonString("forward(10)")
    val pythonTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Python, Var[ProgrammingState](python), _ => ())
    assertEquals(pythonTab.associatedLanguage, AppLanguage.Python)
    assertEquals[ProgrammingState, ProgrammingState](pythonTab.associatedVar.now(), python)
    assert(pythonTab.editorElement.isInstanceOf[CodeMirrorEditor])

    val java = ProgrammingStateJavaString("class A {}")
    val javaTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Java, Var[ProgrammingState](java), _ => ())
    assertEquals(javaTab.associatedLanguage, AppLanguage.Java)
    assertEquals[ProgrammingState, ProgrammingState](javaTab.associatedVar.now(), java)
    assert(javaTab.editorElement.isInstanceOf[JavaFunctionBasedEditor])

    val snap = ProgrammingStateSnapXml.mini
    val snapTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](snap), _ => ())
    assertEquals(snapTab.associatedLanguage, AppLanguage.SnapLanguage)
    assertEquals[ProgrammingState, ProgrammingState](snapTab.associatedVar.now(), snap)
    assert(snapTab.editorElement.isInstanceOf[SnapEditor.SnapCodeEditor])
  }

  test("ProgrammingState converts itself at the Snap editor boundary") {
    val snap = ProgrammingStatePythonString("forward(10)").toSnapXml
    assert(snap.snapXml.contains("<project"), clue = snap.snapXml.take(120))
    assert(snap.snapXml.contains("forward"), clue = snap.snapXml)
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
  }

  test("state representation chooses its matching initial tab") {
    assertEquals(EvaEditor.tabFor(ProgrammingStatePythonString("pass")), EvaEditor.Tab.Python)
    assertEquals(EvaEditor.tabFor(ProgrammingStateJavaString("class A {}")), EvaEditor.Tab.Java)
    assertEquals(EvaEditor.tabFor(ProgrammingStateSnapXml.mini), EvaEditor.Tab.Snap)
  }

  test("unfinished sources and full Java classes open without deriving other representations") {
    val sources: List[ProgrammingState] = List(
      ProgrammingStateSnapXml.empty,
      ProgrammingStateJavaString("\n\tpublic class Drawing {\r\n  public static void main(String[] args) {\r\n"),
      ProgrammingStateJavaString("\npublic class Drawing {\r\n  public static void main(String[] args) {}\r\n}\r\n\t "),
      ProgrammingStatePythonString("\tdef draw(\r\n"),
      ProgrammingStateSnapXml("<project><scripts><script><block s=\"wait\"><l>1</l></block></script></scripts></project>")
    )
    sources.foreach { source =>
      var published = List.empty[ProgrammingState]
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        next => published = published :+ next)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.tabFor(source))
      assertEquals(editor.conversionError.now(), None)
      assertEquals(published, Nil)
    }
  }

  test("matching unfinished Java and Python sources open with only their configured language") {
    val sources: List[(AppLanguage.ProgrammingLanguage, ProgrammingState, EvaEditor.Tab)] = List(
      (AppLanguage.Java, ProgrammingStateJavaString("\npublic class Drawing {\r\n\t"), EvaEditor.Tab.Java),
      (AppLanguage.Python, ProgrammingStatePythonString("\tdef draw(\r\n"), EvaEditor.Tab.Python)
    )
    sources.foreach { (language, source, tab) =>
      val editor = editorFor(source, testingConfig.copy(enabledLanguages = List(language)))
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.conversionError.now(), None)
    }
  }

  test("a configuration without the source language retains the original draft") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source),
      testingConfig.copy(enabledLanguages = List(AppLanguage.Python)),
      next => published = published :+ next)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
    assertEquals(published, Nil)
  }

  test("text navigation before mounting Snap does not require its browser runtime") {
    val source = ProgrammingStatePythonString("forward(12)").toSnapXml
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Python)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
    assertEquals(editor.conversionError.now(), None)
  }

  test("disabled language selections and edits are ignored") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source),
      testingConfig.copy(enabledLanguages = List(AppLanguage.Java)),
      next => published = published :+ next)
    val disabledEdits: List[(EvaEditor.Tab, ProgrammingState)] = List(
      EvaEditor.Tab.Python -> ProgrammingStatePythonString("forward(99)"),
      EvaEditor.Tab.Snap -> ProgrammingStateSnapXml.empty
    )
    disabledEdits.foreach { (tab, edited) =>
      editor.select(tab)
      editor.publish(tab, edited)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("selecting the current tab preserves an unfinished source exactly") {
    val source = ProgrammingStateJavaString("\n public class Drawing {\r\n\t\n")
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Java)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(editor.conversionError.now(), None)
  }

  test("failed switches retain raw unsupported Java classes and unfinished drafts") {
    val sources = List(
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic static void main(String[] args) { double side = 25; }\r\n}\r\n\t "),
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    )
    sources.foreach { source =>
      var published = List.empty[ProgrammingState]
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        next => published = published :+ next)
      List(EvaEditor.Tab.Python, EvaEditor.Tab.Snap).foreach { tab =>
        editor.select(tab)
        assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
        assertEquals(editor.currentState(), source)
        assert(editor.conversionError.now().nonEmpty)
      }
      assertEquals(published, Nil)
    }
  }

  test("viewing converted source does not publish or normalize the stored draft") {
    val source = ProgrammingStatePythonString("\nforward(12)\n\n  ")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    List(EvaEditor.Tab.Java, EvaEditor.Tab.Python).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("only an active editor edit updates the draft and publishes once") {
    val source = ProgrammingStateJavaString("\nclass Drawing {\r\n\t")
    val edited = ProgrammingStateJavaString("\nclass Drawing {\r\n\tpublic void draw(\r\n")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml.mini)
    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(99)"))
    assertEquals(editor.currentState(), source)
    assertEquals(published, Nil)

    editor.publish(EvaEditor.Tab.Java, edited)
    assertEquals(editor.currentState(), edited)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(published, List(edited))
    editor.publish(EvaEditor.Tab.Java, edited)
    assertEquals(published, List(edited))

    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(42)"))
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml.empty)
    assertEquals(editor.currentState(), edited)
    assertEquals(published, List(edited))
  }

  test("Snap edits retain additional floating objects and publish the retained state") {
    val source = ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
      "<project/>", List("\nwatcher\n", "comment\r\n\t "))
    val edited = source.copy(snapXml = "<project name=\"changed\"/>")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml(edited.snapXml))
    assertEquals(editor.currentState(), edited)
    assertEquals(published, List(edited))
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
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    val fingerprint = ProgrammingState.fingerprint(source)
    val factory = new ControlledRunnerFactory
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
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
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
      assertEquals(ProgrammingState.fingerprint(editor.currentState()), fingerprint)
      assertEquals(published, Nil)
    }
  }

  test("the turtle editor uses its injected Java runner for checked class execution") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = new EvaEditorTurtle(Var[ProgrammingState](source), testingConfig,
      TurtleGraphic.TurtleGraphicProgram(Nil), javaRunnerFactory = factory.create)
    val result = editor.getCurrentTurtleCommands()
    assertEquals(factory.runners.size, 1)
    factory.runners.head.complete()
    result.map { commands =>
      assertEquals(commands, mappedCommands())
      assertEquals(editor.currentState(), source)
    }
  }

  test("unfinished classes and invalid Java main programs reject before creating a runner") {
    val sources = List(
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\t"),
      ProgrammingStateJavaString("class Drawing {}"),
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { double side = 25; } }"),
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(missing); } }")
    )
    Future.sequence(sources.map { source =>
      val factory = new ControlledRunnerFactory
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        javaRunnerFactory = factory.create)
      val stored = ProgrammingExercise.StateSerializer.serialize(source)
      editor.getCurrentTurtleCommands().failed.map { _ =>
        assertEquals(factory.runners.size, 0)
        assertEquals(editor.currentState(), source)
        assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
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
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    val factory = new ControlledRunnerFactory
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
    val error = new IllegalStateException("worker failed")
    val result = editor.getCurrentTurtleCommands()
    factory.runners.head.requests.head.result.failure(error)
    result.failed.map { actual =>
      assert(actual eq error)
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
    }
  }

  test("a second Java run rejects while the first run remains active") {
    val factory = new ControlledRunnerFactory
    val editor = new EvaEditorPlain(Var[ProgrammingState](javaSource()), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](javaSource()), testingConfig,
      javaRunnerFactory = () => runner)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
    val first = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    editor.stopJavaExecution()
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](initial), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
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
    val editor = new EvaEditorPlain(Var[ProgrammingState](javaSource()), testingConfig,
      javaRunnerFactory = () => { calls += 1; if calls == 1 then throw error else runner })
    editor.getCurrentTurtleCommands().failed.flatMap { failure =>
      assert(failure eq error)
      val retry = editor.getCurrentTurtleCommands()
      assertEquals(calls, 2)
      assertEquals(runner.requests.size, 1)
      runner.complete()
      retry.map(commands => assertEquals(commands, mappedCommands()))
    }
  }

  test("a successful tab switch releases a Java runner without requiring a mounted editor") {
    val factory = new ControlledRunnerFactory
    val editor = new EvaEditorPlain(Var[ProgrammingState](javaSource()), testingConfig,
      javaRunnerFactory = factory.create)
    val result = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    runner.complete()
    result.map { _ =>
      editor.state.set(ProgrammingStateJavaString("forward(12);"))
      editor.select(EvaEditor.Tab.Python)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
      assertEquals(editor.conversionError.now(), None)
      assertEquals(runner.closeCalls, 1)
    }
  }

  test("a failed tab switch leaves the pending Java run and its raw source intact") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      javaRunnerFactory = factory.create)
    val result = editor.getCurrentTurtleCommands()
    val runner = factory.runners.head
    editor.select(EvaEditor.Tab.Python)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assert(editor.conversionError.now().nonEmpty)
    assertEquals(runner.cancelCalls, 0)
    assertEquals(runner.closeCalls, 0)
    runner.complete()
    result.map { commands =>
      assertEquals(commands, mappedCommands())
      assertEquals(editor.currentState(), source)
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
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        javaRunnerFactory = factory.create)
      editor.getCurrentTurtleCommands().map { commands =>
        assertEquals(commands, List(TurtleCommand[Double]("forward", List(12.0))))
        assertEquals(factory.runners.size, 0)
        assertEquals(editor.currentState(), source)
      }
    }).map(_ => ())
  }
}
