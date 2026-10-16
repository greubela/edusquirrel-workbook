package it.evadid.homepage.webElements.editor.code
import munit.FunSuite
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class TurtleExecutionPanelSpec extends EditorDomSuite {
  private val mountedPanels = Var(Map.empty[TurtleExecutionPanel, () => Unit])
  private def mountPanel(panel: TurtleExecutionPanel): Unit = {
    val close = mount(panel.getDomElement())
    mountedPanels.update(_ + (panel -> close))
  }
  private def unmountPanel(panel: TurtleExecutionPanel): Unit = {
    mountedPanels.now().get(panel).foreach(_())
    mountedPanels.update(_ - panel)
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
    mountPanel(panel)
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
      val stored = ProgrammingState.serializer.serialize(original)
      val panel = new TurtleExecutionPanel(state, () => Future.successful(commands), () => (), Some(target), policy)
      mountPanel(panel)
      panel.run()
      Future.unit.flatMap(_ => Future.unit.map { _ =>
        val matches = policy == TurtleDrawingPolicy.Coverage
        assertEquals(panel.status.now(), TurtleExecutionPanel.Status.Ready(commands, Some(matches)))
        assertEquals(ProgrammingState.serializer.serialize(state.now()), stored)
        unmountPanel(panel)
      })
    }).map(_ => ())
  }

  test("drawing execution reads the current central answer without capturing an editor") {
    val state = Var[ProgrammingState](ProgrammingStatePythonString("forward(5)"))
    val commands = List(TurtleCommand[Double]("forward", List(10.0)))
    val panel = new TurtleExecutionPanel(state, () => {
      assertEquals(state.now(), ProgrammingStatePythonString("forward(10)"))
      Future.successful(commands)
    }, () => ())
    mountPanel(panel)
    state.set(ProgrammingStatePythonString("forward(10)"))
    panel.run()
    Future.unit.flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), TurtleExecutionPanel.Status.Ready(commands, None))
      unmountPanel(panel)
    })
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
        unmountPanel(fixture.panel)
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
      unmountPanel(fixture.panel)
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
      unmountPanel(fixture.panel)
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
    mountPanel(panel)
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
      unmountPanel(panel)
    })
  }

  test("Java execution panel unmount clears results and rejects late completion") {
    import TurtleExecutionPanel.Status
    val fixture = new PanelFixture
    fixture.panel.run()
    unmountPanel(fixture.panel)
    assertEquals(fixture.stops, 1)
    fixture.requests.head.success(squareDrawing())
    fixture.requests.head.future.flatMap(_ => Future.unit.map { _ =>
      assertEquals(fixture.panel.status.now(), Status.Idle)
      mountPanel(fixture.panel)
      fixture.panel.run()
      assertEquals(fixture.requests.size, 2)
      unmountPanel(fixture.panel)
    })
  }

  test("Java execution panel catches synchronous failure without fabricating a drawing") {
    import TurtleExecutionPanel.Status
    val source = Var[ProgrammingState](ProgrammingStateJavaString("class Drawing {}"))
    val panel = new TurtleExecutionPanel(source, () => throw IllegalStateException("unavailable"), () => ())
    panel.run()
    Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Failed("unavailable"))
      unmountPanel(panel)
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
    unmountPanel(fixture.panel)
  }

  test("shared drawing panel accepts a Python source without converting or publishing it") {
    import TurtleExecutionPanel.Status
    val original = ProgrammingStatePythonString("forward(25)")
    val source = Var[ProgrammingState](original)
    val panel = new TurtleExecutionPanel(source, () => Future.successful(squareDrawing()), () => ())
    mountPanel(panel)
    panel.run()
    Future.unit.flatMap(_ => Future.unit.map { _ =>
      assertEquals(panel.status.now(), Status.Ready(squareDrawing(), None))
      assertEquals(source.now(), original)
      unmountPanel(panel)
    })
  }

}
