package it.evadid.homepage.webElements.editor.code

import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingStateSnapXmlHelper

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.state.observable.ObservableValue
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorExtension, EvaEditorPlain, EvaEditorTurtle, EvaProgrammingTab}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlProgrammingExerciseFullJavaRenderer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableState}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaProgrammingTab}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import munit.FunSuite
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorDiagnostics

import java.util.concurrent.CancellationException
import java.time.LocalDateTime
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class EvaEditorSpec extends FunSuite {
  private val testingConfig = EvaEditorConfig(snapConfig = SnapCodeEditorConfig.Testing)

  private class RecordingSyncControl extends SyncControl {
    var stores = Vector.empty[List[InteractionVariable[?]]]
    override val syncLogger: SyncLogger = SyncLogger(
      Logger.withNameAndPrefixes(Some("EvaEditorSpec"), PrintToStdLogger.printNothing))
    override def requestStore(from: List[InteractionVariable[?]]): Future[?] = {
      stores :+= from
      Future.unit
    }
    override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?] =
      throw IllegalStateException("Unexpected cache refresh.")
    override def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariable[?]]): Future[?] =
      throw IllegalStateException("Unexpected cache store check.")
    override def createObservableReport[T](forVariable: InteractionVariable[T]): ObservableValue[SyncControl.InteractionVariableSyncReport[T]] =
      throw IllegalStateException("Unexpected observable sync report.")
    override def createCurrentReport[T](forVariable: InteractionVariable[T]): SyncControl.InteractionVariableSyncReport[T] =
      throw IllegalStateException("Unexpected current sync report.")
  }

  private def squareDrawing(side: Double = 25, turn: Double = 90): List[TurtleCommand[Double]] =
    List.fill(4)(List(TurtleCommand[Double]("forward", List(side)), TurtleCommand[Double]("right", List(turn)))).flatten

  private class PanelFixture {
    val source = Var[ProgrammingState](ProgrammingStateJavaString("class Drawing {}"))
    var requests = Vector.empty[Promise[List[TurtleCommand[Double]]]]
    var stops = 0
    val panel = new TurtleExecutionPanel(source,
      () => { val result = Promise[List[TurtleCommand[Double]]](); requests :+= result; result.future },
      () => stops += 1, Some(TurtleGraphic.TurtleGraphicProgram(squareDrawing())))
    panel.activate()
  }

  test("drawing runs follow the configured policy for Java, Python and Snap sources") {
    val sources: List[ProgrammingState] = List(ProgrammingStateJavaString("forward(5); forward(5);"),
      ProgrammingStatePythonString("forward(5)\nforward(5)"), ProgrammingStateSnapXml.empty)
    val commands = List(TurtleCommand[Double]("forward", List(5.0)), TurtleCommand[Double]("forward", List(5.0)))
    val target = TurtleGraphic.TurtleGraphicProgram(List(TurtleCommand[Double]("forward", List(10.0))))
    Future.sequence(for {
      original <- sources
      policy <- TurtleDrawingPolicy.values.toList
    } yield {
      val state = Var[ProgrammingState](original)
      val stored = ProgrammingExercise.StateSerializer.serialize(original)
      val panel = new TurtleExecutionPanel(state, () => Future.successful(commands), () => (), Some(target), policy)
      panel.activate()
      panel.run()
      Future.unit.flatMap(_ => Future.unit.map { _ =>
        val matches = policy == TurtleDrawingPolicy.Coverage
        assertEquals(panel.status.now(), TurtleExecutionPanel.Status.Ready(commands, Some(matches)))
        assertEquals(ProgrammingExercise.StateSerializer.serialize(state.now()), stored)
        panel.deactivate()
      })
    }).map(_ => ())
  }

  test("pending editor changes are captured before a drawing run starts") {
    val state = Var[ProgrammingState](ProgrammingStatePythonString("forward(5)"))
    var stops = 0
    var captured = state.now()
    val commands = List(TurtleCommand[Double]("forward", List(10.0)))
    val panel = new TurtleExecutionPanel(state, () => {
      assertEquals(captured, ProgrammingStatePythonString("forward(10)"))
      Future.successful(commands)
    }, () => stops += 1, prepare = () => {
      state.set(ProgrammingStatePythonString("forward(10)"))
      captured = state.now()
    })
    panel.activate()
    panel.run()
    Future.unit.flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), TurtleExecutionPanel.Status.Ready(commands, None))
      assertEquals(stops, 0)
      panel.deactivate()
    })
  }

  test("a failed source capture prevents execution without losing the draft") {
    val original = ProgrammingStateJavaString("class Drawing {")
    val state = Var[ProgrammingState](original)
    var runs = 0
    val panel = new TurtleExecutionPanel(state, () => { runs += 1; Future.successful(Nil) }, () => (),
      prepare = () => throw IllegalStateException("unavailable"))
    panel.run()
    assertEquals(panel.status.now(), TurtleExecutionPanel.Status.Failed("unavailable"))
    assertEquals(runs, 0)
    assertEquals(state.now(), original)
  }

  test("Java drawing comparison rejects wrong lengths, turns, empty drawings and extra strokes") {
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    assert(TurtleExecutionPanel.compare(squareDrawing(), target))
    for commands <- List(squareDrawing(24), squareDrawing(26), squareDrawing(25, 89), squareDrawing(25, 45),
      Nil, squareDrawing() :+ TurtleCommand[Double]("backward", List(10.0))) do
      assert(!TurtleExecutionPanel.compare(commands, target), clue = commands)
  }

  test("Java drawing display rejects accumulated coordinate overflow without requiring a target") {
    intercept[IllegalArgumentException](TurtleExecutionPanel.validateDrawing(
      List.fill(2)(TurtleCommand[Double]("forward", List(1e308)))))
    intercept[IllegalArgumentException](TurtleExecutionPanel.validateDrawing(
      List(TurtleCommand[Double]("goto", List(-1e308, 0.0)), TurtleCommand[Double]("goto", List(1e308, 0.0)))))
    intercept[IllegalArgumentException](TurtleExecutionPanel.validateDrawing(
      List(TurtleCommand[Double]("forward", List(Double.MaxValue)))))
    TurtleExecutionPanel.validateDrawing(squareDrawing(10.0 / 3.0))
    TurtleExecutionPanel.validateDrawing(Nil)
  }

  test("Java drawing comparison accepts split strokes and ignores duplicate tracing") {
    val split = List.fill(4)(List(TurtleCommand[Double]("forward", List(12.0)),
      TurtleCommand[Double]("forward", List(13.0)), TurtleCommand[Double]("right", List(90.0)))).flatten
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    assert(TurtleExecutionPanel.compare(split, target))
    assert(TurtleExecutionPanel.compare(squareDrawing() ++ squareDrawing(), target))
  }

  test("Java drawing comparison uses pen-up moves for disconnected targets without drawing their gaps") {
    import it.evadid.core.datastructures.geometry.Point
    val target = TurtleGraphic.TurtleLineBasedProgram(List(
      TurtleGraphic.Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      TurtleGraphic.Line(Point(20.0, 0.0), Point(30.0, 0.0))))
    assert(TurtleExecutionPanel.compare(target.toTurtleProgram.toList, target))
    assert(!TurtleExecutionPanel.compare(List(TurtleCommand[Double]("forward", List(30.0))), target))
  }

  test("Java drawing comparison bounds huge traces and rejects unsupported or non-finite commands") {
    val target = TurtleGraphic.TurtleGraphicProgram(squareDrawing())
    for commands <- List(List(TurtleCommand[Double]("forward", List(Int.MaxValue.toDouble))),
      List(TurtleCommand[Double]("forward", List(Double.PositiveInfinity))),
      List(TurtleCommand[Double]("forward", List(Double.NaN))),
      List(TurtleCommand[Double]("circle", Nil)), List.fill(100)(squareDrawing()).flatten) do
      intercept[IllegalArgumentException](TurtleExecutionPanel.compare(commands, target))
    val empty = TurtleGraphic.TurtleGraphicProgram(Nil)
    assert(TurtleExecutionPanel.compare(Nil, empty))
    assert(!TurtleExecutionPanel.compare(squareDrawing(), empty))
  }

  test("Java execution panel publishes assessed drawing only after successful completion") {
    import TurtleExecutionPanel.Status
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
    import TurtleExecutionPanel.Status
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
    import TurtleExecutionPanel.Status
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

  test("Snap metadata-only A-to-B-to-A restores reject stale drawing results") {
    import TurtleExecutionPanel.Status
    val original = ProgrammingStateSnapXml(ProgrammingStateSnapXml.mini.snapXml, List(" watcher A "))
    val changed = original.copy(legacyFloatingObjects = List(" watcher B "))
    assertNotEquals(ProgrammingState.fingerprint(original), ProgrammingState.fingerprint(changed))
    val source = Var[ProgrammingState](original)
    var requests = Vector.empty[Promise[List[TurtleCommand[Double]]]]
    var stops = 0
    val panel = new TurtleExecutionPanel(source,
      () => { val result = Promise[List[TurtleCommand[Double]]](); requests :+= result; result.future },
      () => stops += 1)
    panel.activate()
    panel.run()
    source.set(original.copy())
    assertEquals(panel.status.now(), Status.Running)
    assertEquals(stops, 0)
    source.set(changed)
    source.set(original)
    assertEquals(panel.status.now(), Status.Idle)
    assertEquals(stops, 1)
    assertEquals(ProgrammingState.fingerprint(source.now()), ProgrammingState.fingerprint(original))
    panel.run()
    val newest = requests.last
    requests.head.success(squareDrawing())
    requests.head.future.flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Running)
      newest.success(squareDrawing(24))
    }).flatMap(_ => newest.future).flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Ready(squareDrawing(24), None))
      assertEquals(source.now(), original)
      panel.deactivate()
    })
  }

  test("Java execution panel unmount clears results and rejects late completion") {
    import TurtleExecutionPanel.Status
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
    import TurtleExecutionPanel.Status
    val source = Var[ProgrammingState](ProgrammingStateJavaString("class Drawing {}"))
    val panel = new TurtleExecutionPanel(source, () => throw IllegalStateException("unavailable"), () => ())
    panel.run()
    Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Failed("unavailable"))
      panel.deactivate()
    }
  }

  test("Java execution panel does not launch a run stopped by a status observer") {
    import TurtleExecutionPanel.Status
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

  test("shared drawing panel accepts a Python source without converting or publishing it") {
    import TurtleExecutionPanel.Status
    val original = ProgrammingStatePythonString("forward(25)")
    val source = Var[ProgrammingState](original)
    val panel = new TurtleExecutionPanel(source, () => Future.successful(squareDrawing()), () => ())
    panel.activate()
    panel.run()
    Future.unit.flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Ready(squareDrawing(), None))
      assertEquals(source.now(), original)
      panel.deactivate()
    })
  }

  private def editorFor(source: ProgrammingState, config: EvaEditorConfig = testingConfig): EvaEditor =
    new EvaEditorPlain(Var[ProgrammingState](source), config)

  private def javaPlain(
      state: Var[ProgrammingState],
      config: EvaEditorConfig,
      onStateEdited: ProgrammingState => Unit = _ => (),
      runnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
  ): EvaEditorPlain =
    EvaEditorPlain(state, config, onStateEdited,
      extensions = List(new JavaTurtleEditorExtension(state, runnerFactory = runnerFactory)))

  private def javaExtension(editor: EvaEditor): JavaTurtleEditorExtension =
    editor.extensions.collectFirst { case extension: JavaTurtleEditorExtension => extension }
      .getOrElse(fail("The Java execution extension is missing."))

  test("a plain Java editor cannot run a full class without an execution extension") {
    val source = javaSource()
    val editor = EvaEditorPlain(Var[ProgrammingState](source),
      testingConfig.copy(enabledLanguages = List(AppLanguage.Java)))
    assertEquals(editor.extensions, Nil)
    editor.getCurrentTurtleCommands().failed.map { _ =>
      assertEquals(editor.currentState(), source)
      editor.onFullscreenClose()
    }
  }

  test("Eva routes execution and lifecycle through its configured extensions") {
    val source = ProgrammingStateJavaString("forward(12);")
    val result = Promise[List[TurtleCommand[Double]]]()
    var offered = Vector.empty[ProgrammingState]
    var executions = 0
    var closes = Vector.empty[String]
    val unrelated = new EvaEditorExtension {
      override def turtleCommands(current: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = {
        offered :+= current
        None
      }
      override def close(): Unit = closes :+= "unrelated"
    }
    val execution = new EvaEditorExtension {
      override def turtleCommands(current: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] =
        Some(() => { executions += 1; result.future })
      override def close(): Unit = closes :+= "execution"
    }
    val editor = EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      extensions = List(unrelated, execution))
    val pending = editor.getCurrentTurtleCommands()
    assertEquals(offered, Vector(source))
    assertEquals(executions, 1)
    result.success(mappedCommands())
    pending.map { commands =>
      assertEquals(commands, mappedCommands())
      assertEquals(editor.currentState(), source)
      editor.select(EvaEditor.Tab.Python)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
      assertEquals(closes, Vector("unrelated", "execution"))
      editor.onFullscreenClose()
      assertEquals(closes, Vector("unrelated", "execution", "unrelated", "execution"))
    }
  }

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

  test("the full Java renderer binding restores raw drafts and stores an editor change once") {
    val element = ProgrammingExerciseFullJava("full-java-bound-draft")
    val variable = element.interactionVariable
    val restored = ProgrammingStateJavaString("\n\tpublic class Restored {\r\n  public static void main(\r\n\t ")
    val remoteState = InteractionVariableState[ProgrammingState](restored, UpdateImportance.MAJOR,
      LocalDateTime.now().minusSeconds(10))
    variable.updateHistory(_ => InteractionVariableHistory(Set(remoteState)))
    val sync = new RecordingSyncControl
    val bound = HtmlProgrammingExerciseFullJavaRenderer.editorState(element, sync)
    val javaEditor = new JavaFunctionBasedEditor(bound)
    val editor = EvaEditorPlain(bound, testingConfig.copy(enabledLanguages = List(AppLanguage.Java)))
    assertEquals(bound.now(), restored)
    assertEquals(editor.currentState(), restored)
    assertEquals(javaEditor.state.now(), restored)
    assertEquals(sync.stores.size, 0)

    val edited = ProgrammingStateJavaString("\n\tpublic class Restored {\r\n  public static void main(String[] args) {\r\n\t ")
    editor.publish(EvaEditor.Tab.Java, edited)
    assertEquals(variable.currentValue, edited)
    assertEquals(javaEditor.state.now(), edited)
    assert(variable.history.events.contains(remoteState))
    assertEquals(variable.history.lastStateOption.map(_.value), Some(edited))
    assertEquals(variable.history.lastStateOption.map(_.updateImportance), Some(UpdateImportance.MAJOR))
    assertEquals(sync.stores.size, 1)
    assertEquals(sync.stores.head.size, 1)
    assert(sync.stores.head.head eq variable)

    val history = variable.history
    editor.publish(EvaEditor.Tab.Java, edited)
    bound.set(edited.copy())
    variable.updateHistory(_.withAddedEvents(history))
    assertEquals(variable.history, history)
    assertEquals(sync.stores.size, 1)
    assertEquals(element.elementId, "full-java-bound-draft")
    assert(variable.underlyingInteraction eq element)
    assertEquals(variable.keyForSerialization, "full-java-bound-draft_history")
    assertEquals(variable.serializedHistory.lastStateOption.map(_.serializedValue),
      Some(ProgrammingExercise.StateSerializer.serialize(edited)))
    editor.onFullscreenClose()
  }

  test("the full Java renderer binding restores an invalid remote draft without storing or delivering an old run") {
    val element = ProgrammingExerciseFullJava("full-java-bound-restore", startingProgram = javaSource().code)
    val variable = element.interactionVariable
    val initial = javaSource()
    val initialState = InteractionVariableState[ProgrammingState](initial, UpdateImportance.MAJOR,
      LocalDateTime.now().minusSeconds(10))
    variable.updateHistory(_ => InteractionVariableHistory(Set(initialState)))
    val sync = new RecordingSyncControl
    val bound = HtmlProgrammingExerciseFullJavaRenderer.editorState(element, sync)
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(bound, testingConfig.copy(enabledLanguages = List(AppLanguage.Java)),
      runnerFactory = factory.create)
    val pending = editor.getCurrentTurtleCommands()
    val previous = factory.runners.head
    assertEquals(previous.requests.size, 1)

    val restored = ProgrammingStateJavaString("\r\n\tpublic class Restored {\r\n  static void draw(int distance) {\n\n\t ")
    val restoredState = InteractionVariableState[ProgrammingState](restored, UpdateImportance.MAJOR,
      LocalDateTime.now().plusSeconds(1))
    variable.updateHistory(_.withAddedEvent(restoredState))
    assertEquals(bound.now(), restored)
    assertEquals(editor.currentState(), restored)
    assertEquals(variable.currentValue, restored)
    assertEquals(variable.history.events, Set(initialState, restoredState))
    assertEquals(variable.history.lastStateOption, Some(restoredState))
    assertEquals(sync.stores.size, 0)
    assertEquals(previous.cancelCalls, 1)
    assertEquals(previous.closeCalls, 1)
    pending.failed.flatMap { error =>
      assert(error.isInstanceOf[CancellationException])
      previous.complete(execution = completedJava(999))
      previous.requests.head.result.future.flatMap(_ => Future.unit).flatMap { _ =>
        editor.getCurrentTurtleCommands().failed.map { _ =>
          assertEquals(factory.runners.size, 1)
          assertEquals(bound.now(), restored)
          assertEquals(editor.currentState(), restored)
          assertEquals(variable.currentValue, restored)
          assertEquals(variable.keyForSerialization, "full-java-bound-restore_history")
          assertEquals(variable.serializedHistory.lastStateOption.map(_.serializedValue),
            Some(ProgrammingExercise.StateSerializer.serialize(restored)))
          assertEquals(sync.stores.size, 0)
          editor.onFullscreenClose()
        }
      }
    }
  }

  private def rejectsPartialJavaExecution(status: T.Status): Future[Unit] = {
    val source = javaSource()
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    val factory = new ControlledRunnerFactory
    var published = List.empty[ProgrammingState]
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
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
    val snap = ProgrammingStateSnapXmlHelper.mini
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
    val initial = ProgrammingStateSnapXmlHelper.mini
    var published: Option[ProgrammingState] = None
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](initial), next => published = Some(next))
    val next = ProgrammingStateSnapXmlHelper.empty
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

    val snap = ProgrammingStateSnapXmlHelper.mini
    val snapTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](snap), _ => ())
    assertEquals(snapTab.associatedLanguage, AppLanguage.SnapLanguage)
    assertEquals[ProgrammingState, ProgrammingState](snapTab.associatedVar.now(), snap)
    assert(snapTab.editorElement.isInstanceOf[SnapEditor.SnapCodeEditor])
  }

  test("prepared text tabs retain their supplied binding and unfinished source exactly") {
    val sources: List[(AppLanguage.ProgrammingLanguage, ProgrammingState)] = List(
      AppLanguage.Java -> ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic void draw(\r\n  "),
      AppLanguage.Python -> ProgrammingStatePythonString("\tdef draw(\r\n\n  ")
    )
    sources.foreach { (language, source) =>
      val local = Var[ProgrammingState](source)
      val stored = ProgrammingExercise.StateSerializer.serialize(source)
      var published = List.empty[ProgrammingState]
      val tab = EvaProgrammingTab.prepared(testingConfig, language, local,
        next => published = published :+ next)
      assert(tab.associatedVar eq local)
      assertEquals(tab.captureSource(), source)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(local.now()), stored)
      assertEquals(published, Nil)
    }
  }

  test("a prepared Java tab opens a full class without deriving another representation") {
    val source = ProgrammingStateJavaString(
      "\npublic class Drawing {\r\n\tpublic void draw() {}\r\n\tpublic static void main(String[] args) {}\r\n}\r\n\t ")
    intercept[IllegalArgumentException](source.toSnapXml)
    val local = Var[ProgrammingState](source)
    var published = List.empty[ProgrammingState]
    val tab = EvaProgrammingTab.prepared(testingConfig, AppLanguage.Java, local,
      next => published = published :+ next)
    val editor = tab.editorElement.asInstanceOf[JavaFunctionBasedEditor]
    assert(editor.state eq local)
    assertEquals(tab.captureSource(), source)
    assertEquals(local.now(), source)
    assertEquals(published, Nil)
  }

  test("prepared Python content keeps edits and restored drafts on the supplied binding") {
    val local = Var[ProgrammingState](ProgrammingStatePythonString("forward(10)"))
    var published = List.empty[ProgrammingState]
    val tab = EvaProgrammingTab.prepared(testingConfig, AppLanguage.Python, local,
      next => published = published :+ next)
    val editor = tab.editorElement.asInstanceOf[CodeMirrorEditor]
    val edited = ProgrammingStatePythonString("\n\tdef draw(\r\n  ")
    editor.content.set(edited.code)
    editor.onUserInput(edited.code)
    assertEquals(local.now(), edited)
    assertEquals(tab.captureSource(), edited)
    assertEquals(published, List(edited))

    val restored = ProgrammingStatePythonString("\r\nforward(42)\r\n\t ")
    local.set(restored)
    assertEquals(editor.content.now(), restored.code)
    assertEquals(tab.captureSource(), restored)
    assertEquals(published, List(edited))
  }

  test("a prepared Snap tab captures restored source and legacy metadata from its supplied binding") {
    val initial = ProgrammingStateSnapXml("<project/>", List(" watcher ", "comment\r\n\t "))
    val local = Var[ProgrammingState](initial)
    var published = List.empty[ProgrammingState]
    val tab = EvaProgrammingTab.prepared(testingConfig, AppLanguage.SnapLanguage, local,
      next => published = published :+ next)
    val editor = tab.editorElement.asInstanceOf[SnapEditor.SnapCodeEditor]
    assert(editor.state eq local)
    assertEquals(tab.captureSource(), initial)

    val restored = ProgrammingStateSnapXml("<project name=\"restored\"/>",
      List(" restored watcher\n", " restored comment\r\n"))
    local.set(restored)
    assertEquals(tab.captureSource(), restored)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(local.now()),
      ProgrammingExercise.StateSerializer.serialize(restored))
    assertEquals(published, Nil)
  }

  test("a programming tab forwards fullscreen lifecycle once without changing its supplied state") {
    import com.raquo.laminar.api.L.Element
    import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
    import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorProgrammingTab

    val source = ProgrammingStatePythonString("\tdef draw(\r\n  ")
    val local = Var[ProgrammingState](source)
    var openCalls = 0
    var closeCalls = 0
    val editor = new HtmlAppElement with FullscreenLifecycle {
      override def getDomElement(): Element =
        throw IllegalStateException("Lifecycle forwarding must not request the DOM.")
      override def onFullscreenOpen(): Unit = openCalls += 1
      override def onFullscreenClose(): Unit = closeCalls += 1
    }
    val tab = EvaEditorProgrammingTab[ProgrammingState](local, AppLanguage.Python, _ => editor, _.toPython)
    tab.onFullscreenOpen()
    assertEquals((openCalls, closeCalls), (1, 0))
    assertEquals(local.now(), source)
    tab.onFullscreenClose()
    assertEquals((openCalls, closeCalls), (1, 1))
    assertEquals(local.now(), source)
  }

  test("ProgrammingState converts itself at the Snap editor boundary") {
    val snap = ProgrammingStatePythonString("forward(10)").toSnapXml
    assert(snap.snapXml.contains("<project"), clue = snap.snapXml.take(120))
    assert(snap.snapXml.contains("forward"), clue = snap.snapXml)
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.SQL), "sql")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.C), "c")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Cpp), "cpp")
  }

  test("Java source metadata preserves mixed separators at UTF-16 positions") {
    import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorJavaLineEndings as Endings
    for (source, separator, exceptions) <- List(
      ("", "\n", Vector.empty[Endings.Exception]),
      ("\tclass Drawing {}", "\n", Vector.empty[Endings.Exception]),
      ("a\r\n", "\r\n", Vector.empty[Endings.Exception]),
      ("a\rb\n", "\r", Vector(Endings.Exception(3, "\n"))),
      ("a\r\nb\nc\rd\r\n", "\r\n", Vector(Endings.Exception(3, "\n"), Endings.Exception(5, "\r"))),
      ("😀\r\nx\ny\r", "\r\n", Vector(Endings.Exception(4, "\n"), Endings.Exception(6, "\r")))
    ) do assertEquals(Endings.parse(source), Endings.Metadata(separator, exceptions))
  }

  test("CodeMirror diagnostic normalization rejects invalid lines without clamping them") {
    val raw = js.Array[js.Any](null, js.undefined, js.Dynamic.literal(),
      js.Dynamic.literal(line = 0), js.Dynamic.literal(line = -1), js.Dynamic.literal(line = 4),
      js.Dynamic.literal(line = 1.5), js.Dynamic.literal(line = Double.NaN),
      js.Dynamic.literal(line = Double.PositiveInfinity), js.Dynamic.literal(line = "2"))
    assertEquals(CodeMirrorDiagnostics.normalize(raw, 3).map(_.line), Vector(2))
    Seq[js.Any](null, js.undefined, js.Dynamic.literal(), "diagnostic", 4).foreach { value =>
      assertEquals(CodeMirrorDiagnostics.normalize(value, 3), Vector.empty)
    }
    assertEquals(CodeMirrorDiagnostics.normalize(js.Array(js.Dynamic.literal(line = 1)), 0), Vector.empty)
  }

  test("CodeMirror diagnostics preserve numeric coercion, safe columns and severity defaults") {
    val raw = js.Array(
      js.Dynamic.literal(line = 1, endLine = -1, fromCh = null, toCh = "2.9", severity = "ERROR", message = 7),
      js.Dynamic.literal(line = 2, endLine = 1e12, fromCh = -7, toCh = Double.PositiveInfinity, severity = "INFO"),
      js.Dynamic.literal(line = 3, endLine = Double.NaN, fromCh = js.undefined, severity = " Error ", message = null))
    val entries = CodeMirrorDiagnostics.normalize(raw, 3)
    assertEquals(entries.map(item => (item.line, item.endLine)), Vector((1, 1), (2, 3), (3, 3)))
    assertEquals(entries.map(_.fromCh), Vector(Some(0.0), Some(0.0), None))
    assertEquals(entries.map(_.toCh), Vector(Some(2.0), None, None))
    assertEquals(entries.map(_.severity), Vector("error", "soft", "warning"))
    assertEquals(entries.map(_.message), Vector("7", "", ""))
  }

  test("CodeMirror diagnostic overlap has stable priority, distinct messages and unmarked gaps") {
    import CodeMirrorDiagnostics.*
    val info = Normalized(1, 1, Some(0), Some(2), "soft", "Info")
    val error = Normalized(1, 1, Some(1), Some(3), "error", "Error")
    val expected = LinePlan(Label("error", "Error\nInfo"), Vector(
      Mark(0, 1, Label("soft", "Info")), Mark(1, 2, Label("error", "Error\nInfo")),
      Mark(2, 3, Label("error", "Error"))))
    Seq(Vector(info, error), Vector(error, info)).foreach { items =>
      assertEquals(plan(items, 3), Some(expected))
    }
    assertEquals(plan(Vector(info.copy(toCh = Some(1)), error.copy(fromCh = Some(2))), 3).map(_.marks),
      Some(Vector(Mark(0, 1, Label("soft", "Info")), Mark(2, 3, Label("error", "Error")))))
    assertEquals(plan(Vector(error, error), 3).map(_.label), Some(Label("error", "Error")))
    assertEquals(plan(Vector.empty, 3), None)
  }

  test("CodeMirror diagnostic spans clamp before integer conversion and retain UTF-16 offsets") {
    import CodeMirrorDiagnostics.*
    val entry = Normalized(1, 1, Some(2), Some(1e100), "error", "<img src=x onerror=alert(1)>")
    val planned = plan(Vector(entry), "😀x".length).get
    assertEquals(planned.marks, Vector(Mark(2, 3, Label("error", entry.message))))
    assertEquals("😀x".substring(planned.marks.head.from, planned.marks.head.to), "x")
    Seq(entry.copy(fromCh = Some(99)), entry.copy(toCh = Some(1)), entry.copy(toCh = Some(2)),
      entry.copy(toCh = None), entry.copy(endLine = 2)).foreach { noSpan =>
      assertEquals(plan(Vector(noSpan), 3).map(_.marks), Some(Vector.empty))
    }
    assertEquals(plan(Vector(entry.copy(fromCh = Some(0))), 0).map(_.marks), Some(Vector.empty))
  }

  test("CodeMirror diagnostic overlap agrees with the strongest diagnostic at every character") {
    import CodeMirrorDiagnostics.*
    val random = new scala.util.Random(7249)
    val severities = Vector("soft", "warning", "error")
    (0 until 500).foreach { sample =>
      val entries = Vector.fill(1 + random.nextInt(12)) {
        Normalized(1, 1, Some(random.nextInt(14) - 2), Some(random.nextInt(14) - 2),
          severities(random.nextInt(3)), s"message-${random.nextInt(4)}")
      }
      val marks = plan(entries, 10).get.marks
      (0 until 10).foreach { position =>
        val active = entries.filter(item => item.fromCh.exists(from => math.max(0, from) <= position) && item.toCh.exists(_ > position))
        val actual = marks.filter(mark => mark.from <= position && mark.to > position)
        if active.isEmpty then assertEquals(actual.size, 0, clue = s"$sample/$position")
        else {
          assertEquals(actual.size, 1, clue = s"$sample/$position")
          assertEquals(actual.head.label.severity, severities(active.map(item => severities.indexOf(item.severity)).max))
          val messages = actual.head.label.message.split("\n").toVector
          assertEquals(messages.distinct, messages)
        }
      }
    }
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
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic static void main(String[] args) { double turtle = 25; }\r\n}\r\n\t "),
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

  test("full Java classes switch editable views without publishing a converted answer") {
    val source = ProgrammingStateJavaString("\r\npublic class Drawing { " +
      "static void line(int side) { for (int i = 0; i < 3; i++) { Turtle.forward(side); } } " +
      "public static void main(String[] args) { line(4); } }\r\n\t ")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    List(EvaEditor.Tab.Python, EvaEditor.Tab.Snap, EvaEditor.Tab.Java).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.conversionError.now(), None)
      assertEquals(editor.currentState(), source)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
    }
    assertEquals(published, Nil)
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

  test("Snap edits retain legacy floating metadata and publish the retained state") {
    val source = ProgrammingStateSnapXml(
      "<project/>", List("\nwatcher\n", "comment\r\n\t "))
    val edited = source.withProjectXml("<project name=\"changed\"/>")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml(edited.snapXml))
    assertEquals(editor.currentState(), edited)
    assertEquals(published, List(edited))
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(
      ProgrammingExercise.StateSerializer.serialize(editor.currentState())), edited)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml(edited.snapXml))
    assertEquals(published, List(edited))
  }

  test("failed text conversions cannot replace Snap legacy metadata") {
    val source = ProgrammingStateSnapXml(ProgrammingStateSnapXml.mini.snapXml,
      List("\nwatcher\n", "comment\r\n\t "))
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    List(EvaEditor.Tab.Python -> ProgrammingStatePythonString("forward(99)"),
      EvaEditor.Tab.Java -> ProgrammingStateJavaString("forward(99);")).foreach { (tab, edited) =>
      editor.select(tab)
      editor.publish(tab, edited)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Snap)
      assert(editor.conversionError.now().nonEmpty)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
    }
    assertEquals(published, Nil)
  }

  test("disabled Snap or text views cannot publish over legacy metadata") {
    val source = ProgrammingStateSnapXml(ProgrammingStateSnapXml.mini.snapXml,
      List(" watcher ", "comment\nline two"))
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    val languageSets: List[List[AppLanguage.ProgrammingLanguage]] =
      List(List(AppLanguage.SnapLanguage), List(AppLanguage.Python))
    languageSets.foreach { languages =>
      var published = List.empty[ProgrammingState]
      val editor = new EvaEditorPlain(Var[ProgrammingState](source),
        testingConfig.copy(enabledLanguages = languages), next => published = published :+ next)
      editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(99)"))
      editor.publish(EvaEditor.Tab.Java, ProgrammingStateJavaString("forward(99);"))
      if !languages.contains(AppLanguage.SnapLanguage) then
        editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml.empty)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
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
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
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
      assertEquals(ProgrammingExercise.StateSerializer.serialize(editor.currentState()), stored)
      assertEquals(ProgrammingState.fingerprint(editor.currentState()), fingerprint)
      assertEquals(published, Nil)
    }
  }

  test("the turtle editor uses its injected Java runner for checked class execution") {
    val source = javaSource()
    val factory = new ControlledRunnerFactory
    val state = Var[ProgrammingState](source)
    val target = TurtleGraphic.TurtleGraphicProgram(Nil)
    val extension = new JavaTurtleEditorExtension(state, runnerFactory = factory.create)
    val editor = EvaEditorTurtle(state, testingConfig, target, editorExtensions = List(extension))
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
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { float side = 25; } }"),
      ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(missing); } }")
    )
    Future.sequence(sources.map { source =>
      val factory = new ControlledRunnerFactory
      val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
        runnerFactory = factory.create)
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
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
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

  test("a successful tab switch releases a Java runner without requiring a mounted editor") {
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](javaSource()), testingConfig,
      runnerFactory = factory.create)
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
    val source = ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { int turtle = 12; Turtle.forward(turtle); } }")
    val factory = new ControlledRunnerFactory
    val editor = javaPlain(Var[ProgrammingState](source), testingConfig,
      runnerFactory = factory.create)
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
