package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.*
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleGraphic
import munit.FunSuite
import org.scalajs.dom
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import it.evadid.homepage.webElements.editor.code.EditorDomSuite

class TurtleJsxGraphRendererGeometrySpec extends FunSuite:
  private def cmd(name: String, args: Double*): TurtleCommand[Double] = TurtleCommand.fromText(name, args.toList)
  private def point(actual: Point[Double], x: Double, y: Double): Unit =
    assertEqualsDouble(actual.x, x, 1e-7)
    assertEqualsDouble(actual.y, y, 1e-7)

  // Record every JSXGraph object and execute the actual renderer's hover callbacks.
  private class Graph(batchUpdates: Boolean = false):
    class Obj(val kind: String, val parents: js.Array[js.Dynamic], val attrs: js.Dynamic):
      val events = scala.collection.mutable.Map.empty[String, js.Function1[js.Any, Unit]]
      val value: js.Dynamic = js.Dynamic.literal(
        on = ((event: String, callback: js.Function1[js.Any, Unit]) => events(event) = callback): js.Function2[String, js.Function1[js.Any, Unit], Unit],
        setAttribute = ((next: js.Dynamic) => js.Object.keys(next.asInstanceOf[js.Object]).foreach(key => attrs.updateDynamic(key)(next.selectDynamic(key)))): js.Function1[js.Dynamic, Unit]
      )
      def fire(event: String): Unit = events(event)(js.undefined)
    val objects = ListBuffer.empty[Obj]
    var bounds = js.Array[Double]()
    var renderer = ""
    var containerClass = ""
    var title = ""
    var axisLabelDisplay = ""
    var rejectCreation = false
    val initialized = ListBuffer.empty[(dom.html.Div, js.Dynamic)]
    val freed = ListBuffer.empty[js.Dynamic]
    val updateCalls = ListBuffer.empty[(String, Int)]
    private def board(): js.Dynamic =
      val created = js.Dynamic.literal(
        create = ((kind: String, parents: js.Array[js.Dynamic], attrs: js.Dynamic) => {
          if rejectCreation then throw IllegalStateException("renderer unavailable")
          val obj = new Obj(kind, parents, attrs)
          objects += obj
          obj.value
        }): js.Function3[String, js.Array[js.Dynamic], js.Dynamic, js.Dynamic],
        update = (() => { updateCalls += (("update", objects.size)); () }): js.Function0[Unit]
      )
      if batchUpdates then
        created.updateDynamic("suspendUpdate")((() => { updateCalls += (("suspend", objects.size)); () }): js.Function0[Unit])
        created.updateDynamic("unsuspendUpdate")((() => { updateCalls += (("resume", objects.size)); () }): js.Function0[Unit])
      created
    def withLibrary[A](test: => A): A =
      val previous = js.Dynamic.global.globalThis.selectDynamic("JXG")
      js.Dynamic.global.globalThis.updateDynamic("JXG")(js.Dynamic.literal(JSXGraph = js.Dynamic.literal(
        initBoard = ((container: dom.html.Div, attrs: js.Dynamic) => {
          bounds = attrs.boundingbox.asInstanceOf[js.Array[Double]]
          renderer = attrs.renderer.asInstanceOf[String]
          title = attrs.title.asInstanceOf[String]
          axisLabelDisplay = attrs.defaultAxes.x.ticks.label.display.asInstanceOf[String]
          val created = board()
          initialized += ((container, created))
          created
        }): js.Function2[dom.html.Div, js.Dynamic, js.Dynamic],
        freeBoard = ((board: js.Dynamic) => freed += board): js.Function1[js.Dynamic, Unit]
      )))
      try test
      finally js.Dynamic.global.globalThis.updateDynamic("JXG")(previous)
    private def container(): dom.html.Div = js.Dynamic.literal(id = "test-board", classList = js.Dynamic.literal(
      add = ((value: String) => containerClass = value): js.Function1[String, Unit]
    )).asInstanceOf[dom.html.Div]
    def render(program: List[TurtleCommand[Double]], expected: List[LineToRender[Double]] = Nil, graphic: Option[TurtleGraphic] = None): Unit =
      withLibrary {
        graphic match
          case Some(value) => TurtleJsxGraphRenderer.render(container(), program, value)
          case None => TurtleJsxGraphRenderer.render(container(), program, expected)
      }
    def renderScene(scene: Scene, label: String): Unit =
      withLibrary { TurtleJsxGraphRenderer.render(container(), scene, label) }
    def ofKind(kind: String): List[Obj] = objects.filter(_.kind == kind).toList
    def parent(obj: Obj, index: Int): Obj = objects.find(_.value == obj.parents(index)).get
    def coords(obj: Obj): Point[Double] = Point(obj.parents(0).asInstanceOf[Double], obj.parents(1).asInstanceOf[Double])
    /** A sector curve's vertex, first and last arc points: it starts and ends at the vertex. */
    def sector(curve: Obj): (Point[Double], Point[Double], Point[Double]) =
      val xs = curve.parents(0).asInstanceOf[js.Array[Double]]
      val ys = curve.parents(1).asInstanceOf[js.Array[Double]]
      assertEquals((xs.head, ys.head), (xs.last, ys.last))
      (Point(xs.head, ys.head), Point(xs(1), ys(1)), Point(xs(xs.length - 2), ys(ys.length - 2)))
    def radius(curve: Obj): Double =
      val (vertex, first, _) = sector(curve)
      math.hypot(first.x - vertex.x, first.y - vertex.y)
    /** The hover label of an angle, if it was created; it shows the degrees while visible. */
    def angleLabel: Option[(String, Boolean)] = ofKind("text").headOption.map(text =>
      (text.parents(2).asInstanceOf[String], js.isUndefined(text.attrs.visible) || text.attrs.visible.asInstanceOf[Boolean]))

  private def withDom[A](test: => A): A =
    val restore = installDom()
    try test
    finally restore()

  /** Runs `test` with the jsdom globals, `graph`'s JSXGraph stand-in and a host element in the document. */
  private def withHost[A](graph: Graph)(test: dom.Element => A): A =
    withDom {
      graph.withLibrary {
        val host = dom.document.createElement("div")
        dom.document.body.appendChild(host)
        test(host)
      }
    }

  private def installDom(): () => Unit =
    val globals = js.Dynamic.global.globalThis
    val window = EditorDomSuite.window
    val names = List("window", "document", "Element", "Node", "Event")
    val previous = names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name))
    names.foreach(name => globals.updateDynamic(name)(window.selectDynamic(name)))
    () =>
      previous.foreach { (name, descriptor) =>
        if js.isUndefined(descriptor) then js.special.delete(globals, name)
        else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
      }

  test("scene renderer preserves neutral lines and uses a nonempty accessible title") {
    val graph = new Graph
    val scene = Scene(List(RenderedLine(Point(0.0, 0.0), Point(10.0, 0.0), LineResult.Neutral, false)), Nil)
    graph.renderScene(scene, "square(25): your drawing")
    assertEquals(graph.title, "square(25): your drawing")
    assertEquals(graph.ofKind("segment").head.attrs.cssClass.asInstanceOf[String], "turtle-line turtle-line--neutral")
    graph.renderScene(Scene(Nil, Nil), "  ")
    assertEquals(graph.title, "Turtle drawing")
  }

  test("mounting multiple scenes passes distinct DOM containers and frees their own boards") {
    val graph = new Graph
    withHost(graph) { host =>
      val mounted = L.render(host, L.div(
        TurtleJsxGraphRenderer.render(Scene(Nil, Nil), "Target"),
        TurtleJsxGraphRenderer.render(Scene(Nil, Nil), "Your drawing")
      ))
      try
        assertEquals(graph.initialized.size, 2)
        assert(graph.initialized.head._1 ne graph.initialized.last._1)
        assert(graph.initialized.head._2 != graph.initialized.last._2)
        assertEquals(graph.freed.toList, Nil)
      finally mounted.unmount()
      assertEquals(graph.freed.toSet, graph.initialized.map(_._2).toSet)
      assertEquals(graph.freed.size, 2)
    }
  }

  test("replacing and remounting a scene frees each concrete board once") {
    val graph = new Graph
    withHost(graph) { host =>
      import L.*
      val shown = Var(true)
      val scene = TurtleJsxGraphRenderer.render(Scene(Nil, Nil), "Target")
      val mounted = L.render(host, div(child.maybe <-- shown.signal.map(value => Option.when(value)(scene))))
      try
        assertEquals(graph.initialized.size, 1)
        shown.set(false)
        assertEquals(graph.freed.toList, List(graph.initialized.head._2))
        shown.set(false)
        assertEquals(graph.freed.size, 1)
        shown.set(true)
        assertEquals(graph.initialized.size, 2)
      finally mounted.unmount()
      assertEquals(graph.freed.toList, graph.initialized.map(_._2).toList)
    }
  }

  test("a changing scene replaces the panel with a fresh board in its default view; equal scenes are kept") {
    val graph = new Graph(batchUpdates = true)
    withHost(graph) { host =>
      import L.*
      val commands = Var(List(cmd("forward", 10), cmd("right", 90), cmd("forward", 10)))
      val expected = TurtleGraphic.TurtleGraphicProgram(commands.now())
      val mounted = L.render(host, div(TurtleJsxGraphRenderer.renderStrokes(commands.signal, expected)))
      try
        assertEquals(graph.initialized.size, 1)
        val firstPanel = host.querySelector(".turtle-gradig-panel")
        commands.set(List(cmd("forward", 5)))
        assertEquals(graph.initialized.size, 2, "a new board, initialized with the new scene's bounds")
        assertEquals(graph.freed.toList, graph.initialized.take(1).map(_._2).toList, "the previous board is released")
        assert(!(host.querySelector(".turtle-gradig-panel") eq firstPanel), "the whole panel is replaced")
        assertEquals(host.querySelectorAll(".turtle-gradig-panel").length, 1)
        commands.set(List(cmd("forward", 5)))
        assertEquals(graph.initialized.size, 2, "an equal scene keeps its panel")
      finally mounted.unmount()
      assertEquals(graph.freed.toList, graph.initialized.map(_._2).toList)
    }
  }

  test("missing JSXGraph is requested, and a failed load produces a visible warning without failing the mount") {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    val restore = installDom()
    val globals = js.Dynamic.global.globalThis
    val previous = globals.selectDynamic("JXG")
    globals.updateDynamic("JXG")(js.undefined)
    val host = dom.document.createElement("div")
    dom.document.body.appendChild(host)
    val mounted = L.render(host, TurtleJsxGraphRenderer.render(Scene(Nil, Nil), "Target"))
    def settled(remaining: Int): scala.concurrent.Future[Unit] =
      if host.textContent.contains("unavailable") || remaining == 0 then scala.concurrent.Future.unit
      else scala.concurrent.Future(()).flatMap(_ => settled(remaining - 1))
    scala.concurrent.Future {
      val script = dom.document.querySelector("script[src$='jsxgraphcore.js']")
      assert(script != null, "the page requests the library")
      // The browser reports a script that cannot be fetched with an error event.
      script.dispatchEvent(new dom.Event("error"))
    }.flatMap(_ => settled(20)).map { _ =>
      assert(host.textContent.contains("drawing preview is unavailable"), host.textContent)
      assert(host.firstElementChild.classList.contains("turtle-gradig-panel--unavailable"))
    }.transform { result =>
      mounted.unmount()
      host.remove()
      globals.updateDynamic("JXG")(previous)
      restore()
      result
    }
  }

  test("a failed scene mount releases its partially created board") {
    val graph = new Graph
    graph.rejectCreation = true
    withHost(graph) { host =>
      val scene = Scene(List(RenderedLine(Point(0.0, 0.0), Point(10.0, 0.0), LineResult.Neutral, false)), Nil)
      val mounted = L.render(host, TurtleJsxGraphRenderer.render(scene, "Target"))
      try
        assert(host.textContent.contains("drawing preview is unavailable"))
        assertEquals(graph.freed.toList, graph.initialized.map(_._2).toList)
      finally mounted.unmount()
      assertEquals(graph.freed.size, 1)
    }
  }

  test("nonfinite coordinates and overflowing bounds never initialize a board") {
    for (start, end) <- List(
      Point(0.0, 0.0) -> Point(Double.NaN, 1.0),
      Point(0.0, 0.0) -> Point(Double.PositiveInfinity, 1.0),
      Point(-Double.MaxValue, 0.0) -> Point(Double.MaxValue, 1.0),
      Point(Double.MaxValue, 0.0) -> Point(Double.MaxValue, 1.0)
    ) do
      val graph = new Graph
      val scene = Scene(List(RenderedLine(start, end, LineResult.Neutral, false)), Nil)
      intercept[IllegalArgumentException](graph.renderScene(scene, "Your drawing"))
      assertEquals(graph.initialized.size, 0)
  }

  test("invalid display bounds produce a warning rather than a mounted board") {
    val graph = new Graph
    withHost(graph) { host =>
      val scene = Scene(List(RenderedLine(Point(0.0, 0.0), Point(Double.NaN, 0.0), LineResult.Neutral, false)), Nil)
      val mounted = L.render(host, TurtleJsxGraphRenderer.render(scene, "Your drawing"))
      try
        assert(host.textContent.contains("outside the supported display range"))
        assertEquals(graph.initialized.size, 0)
      finally mounted.unmount()
      assertEquals(graph.freed.size, 0)
    }
  }

  for
    heading <- List(0.0, 90.0, 180.0, 270.0)
    turn <- List("left", "right")
    degrees <- List(-120.0, -30.0, 30.0, 89.5, 90.0, 90.5, 120.0, 180.0, 270.0)
    penUp <- List(false, true)
  do
    test(s"all object coordinates: heading=$heading $turn($degrees) penUp=$penUp, before/during/after hover") {
      val radians = Math.toRadians(heading)
      val signed = if turn == "left" then degrees else -degrees
      val after = Math.toRadians(heading + signed)
      val vertex = Point(100 * math.cos(radians), -100 * math.sin(radians))
      val end = Point(vertex.x + 80 * math.cos(after), vertex.y - 80 * math.sin(after))
      val program = List(cmd("setheading", heading)) ++ (if penUp then List(cmd("penup")) else Nil) ++
        List(cmd("forward", 100), cmd(turn, degrees), cmd("forward", 80))
      val expected = List(LineToRender(Point(0.0, 0.0), vertex, jump = penUp), LineToRender(vertex, end, jump = penUp))
      val scene = buildScene(program, expected)
      assertEquals(scene.lines.size, 2)
      scene.lines.zip(expected).foreach { (line, target) =>
        point(line.start, target.start.x, target.start.y)
        point(line.end, target.end.x, target.end.y)
        assertEquals(line.jump, penUp)
        assertEquals(line.result, LineResult.Correct)
      }
      assertEquals(scene.angles.size, 1)
      val angle = scene.angles.head
      point(angle.vertex, vertex.x, vertex.y)
      assertEquals(angle.fromHeading, heading)
      assertEquals(angle.degrees, signed)
      assertEquals((angle.lineBefore, angle.lineAfter), (0, 1))
      val graph = new Graph
      graph.render(program, expected)
      assertEquals(graph.renderer, "svg")
      assertEquals(graph.containerClass, "turtle-gradig-panel")
      val segments = graph.ofKind("segment")
      val arc = graph.ofKind("curve").head
      val radius = graph.radius(arc)
      assert(radius > 0 && radius <= 20)
      assert(arc.attrs.hasInnerPoints.asInstanceOf[Boolean], "the whole sector reacts to hovering")
      // The incoming ray points back toward the previous endpoint. Use the
      // effective signed turn to choose the smaller angle between the lines.
      val effectiveTurn = ((signed + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
      val firstHeading = if effectiveTurn > 0 then after else radians + math.Pi
      val lastHeading = if effectiveTurn > 0 then radians + math.Pi else after
      val cornerDegrees = math.abs(180.0 - math.abs(signed) % 360.0)
      def verifyCoordinates(): Unit =
        segments.zip(expected).foreach { (segment, line) =>
          point(graph.coords(graph.parent(segment, 0)), line.start.x, -line.start.y)
          point(graph.coords(graph.parent(segment, 1)), line.end.x, -line.end.y)
          val classes = segment.attrs.cssClass.asInstanceOf[String].split(" ").toSet
          assertEquals(classes.contains("turtle-line--jump"), penUp)
          assert(classes.contains("turtle-line--correct"))
        }
        val (center, first, last) = graph.sector(arc)
        point(first, vertex.x + radius * math.cos(firstHeading), -vertex.y + radius * math.sin(firstHeading))
        point(center, vertex.x, -vertex.y)
        point(last, vertex.x + radius * math.cos(lastHeading), -vertex.y + radius * math.sin(lastHeading))
        graph.ofKind("point").foreach { p =>
          val xy = graph.coords(p)
          assert(xy.x >= graph.bounds(0) && xy.x <= graph.bounds(2))
          assert(xy.y <= graph.bounds(1) && xy.y >= graph.bounds(3))
        }
      verifyCoordinates()
      assertEquals(graph.angleLabel, None, "angle labels are created on first hover")
      (segments :+ arc).foreach { obj =>
        obj.fire("over")
        assertEquals(arc.attrs.cssClass.asInstanceOf[String], "turtle-angle is-emphasized")
        assertEquals(graph.angleLabel, Some((s"${cornerDegrees.toString.stripSuffix(".0")}°", true)))
        verifyCoordinates()
        obj.fire("out")
        assertEquals(arc.attrs.cssClass.asInstanceOf[String], "turtle-angle")
        assertEquals(graph.angleLabel.map(_._2), Some(false))
        verifyCoordinates()
      }
      assertEquals(graph.ofKind("text").size, 1, "one label per angle is reused")
    }

  test("result classes and hover callbacks leave presentation values to CSS") {
    val graph = new Graph
    val actual = List(cmd("forward", 10), cmd("right", 30), cmd("forward", 10))
    val expected = TurtleGraphic.TurtleGraphicProgram(List(cmd("forward", 10), cmd("left", 90), cmd("forward", 10)))
    graph.render(actual, graphic = Some(expected))
    val segments = graph.ofKind("segment")
    assertEquals(segments.map(_.attrs.cssClass.asInstanceOf[String]), List(
      "turtle-line turtle-line--correct", "turtle-line turtle-line--unexpected", "turtle-line turtle-line--missing"
    ))
    val styleFields = Set("strokeColor", "strokeWidth", "fillColor", "fillOpacity", "strokeOpacity", "dash")
    graph.objects.foreach { obj =>
      assert(js.Object.keys(obj.attrs.asInstanceOf[js.Object]).forall(key => !styleFields.contains(key)))
    }
    val endpoint = graph.parent(segments.head, 0)
    assertEquals(endpoint.attrs.cssClass.asInstanceOf[String], "turtle-point")
    segments.head.fire("over")
    assertEquals(endpoint.attrs.cssClass.asInstanceOf[String], "turtle-point is-emphasized")
    assert(endpoint.attrs.name.asInstanceOf[String].nonEmpty)
    segments.head.fire("out")
    assertEquals(endpoint.attrs.cssClass.asInstanceOf[String], "turtle-point")
    assertEquals(endpoint.attrs.name.asInstanceOf[String], "")
  }

  test("absolute movement uses the pen state and breaks pending angles") {
    for penUp <- List(false, true) do
      val program = (if penUp then List(cmd("penup")) else Nil) ++ List(cmd("forward", 10), cmd("left", 45), cmd("goto", 20, 30), cmd("setx", 40), cmd("sety", 50), cmd("home"), cmd("forward", 5))
      val scene = buildScene(program, Nil)
      // The scene is in SVG coordinates (y down); goto, setx and sety take turtle coordinates (y up).
      val targets = List(Point(10.0, 0.0), Point(20.0, -30.0), Point(40.0, -30.0), Point(40.0, -50.0), Point(0.0, 0.0), Point(5.0, 0.0))
      assertEquals(scene.lines.size, targets.size)
      scene.lines.zip(targets).foreach { (line, target) =>
        point(line.end, target.x, target.y)
        assertEquals(line.jump, penUp)
      }
      assertEquals(scene.angles, Nil)
  }

  test("setheading cancels a pending turn; clear and reset discard angle references") {
    val scene = buildScene(List(cmd("forward", 10), cmd("right", 30), cmd("setheading", 90), cmd("forward", 20)), Nil)
    assertEquals(scene.angles, Nil)
    point(scene.lines.last.end, 10, -20)
    for command <- List("clear", "reset") do
      val cleared = buildScene(List(cmd("forward", 10), cmd("right", 90), cmd(command), cmd("forward", 20), cmd("left", 90), cmd("forward", 10)), Nil)
      assertEquals(cleared.lines.size, 2)
      assertEquals((cleared.angles.head.lineBefore, cleared.angles.head.lineAfter), (0, 1))
      if command == "reset" then point(cleared.lines.head.end, 20, 0)
      else point(cleared.lines.head.end, 10, 20)
  }

  test("expected segments match once in either direction and missing segments have correct coordinates") {
    val expected = List(LineToRender(Point(10.0, 0.0), Point(0.0, 0.0)), LineToRender(Point(10.0, 0.0), Point(10.0, 10.0)), LineToRender(Point(2.0, 3.0), Point(4.0, 5.0)))
    val scene = buildScene(List(cmd("forward", 10), cmd("backward", 10), cmd("forward", 10), cmd("right", 90), cmd("forward", 10)), expected)
    assertEquals(scene.lines.map(_.result), List(LineResult.Correct, LineResult.Unexpected, LineResult.Unexpected, LineResult.Correct, LineResult.Missing))
    point(scene.lines.last.start, 2, 3)
    point(scene.lines.last.end, 4, 5)
    val graph = new Graph
    graph.render(Nil, expected)
    graph.ofKind("segment").zip(expected).foreach { (segment, line) =>
      point(graph.coords(graph.parent(segment, 0)), line.start.x, -line.start.y)
      point(graph.coords(graph.parent(segment, 1)), line.end.x, -line.end.y)
    }
  }

  for
    turn <- List("left", "right")
    degrees <- List(30.0, 90.0, 120.0, 270.0)
    missing <- List(false, true)
  do
    test(s"expected graphic overlays: $turn($degrees), missing=$missing, with hover") {
      val expectedProgram = List(cmd("penup"), cmd("forward", 50), cmd("pendown"), cmd(turn, degrees), cmd("forward", 40))
      val expected = TurtleGraphic.TurtleGraphicProgram(expectedProgram)
      val actual = if missing then Nil else expectedProgram
      val scene = buildScene(actual, expected)
      assertEquals(scene.lines.map(_.result), List.fill(2)(if missing then LineResult.Missing else LineResult.Correct))
      assertEquals(scene.lines.map(_.jump), List(true, false))
      val signed = if turn == "left" then degrees else -degrees
      val angle = scene.angles.head
      point(angle.vertex, 50, 0)
      assertEquals(angle.degrees, signed)
      assertEquals((angle.lineBefore, angle.lineAfter), (0, 1))
      val graph = new Graph
      graph.render(actual, graphic = Some(expected))
      val arc = graph.ofKind("curve").head
      val radius = graph.radius(arc)
      val radians = math.toRadians(signed)
      val start = Point(50 - radius, 0.0)
      val end = Point(50 + radius * math.cos(radians), radius * math.sin(radians))
      def verifyCoordinates(): Unit =
        val effectiveTurn = ((signed + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val (first, last) = if effectiveTurn > 0 then (end, start) else (start, end)
        val (center, firstPoint, lastPoint) = graph.sector(arc)
        point(firstPoint, first.x, first.y)
        point(center, 50, 0)
        point(lastPoint, last.x, last.y)
        graph.ofKind("segment").zip(scene.lines).foreach { (segment, line) =>
          point(graph.coords(graph.parent(segment, 0)), line.start.x, -line.start.y)
          point(graph.coords(graph.parent(segment, 1)), line.end.x, -line.end.y)
          assertEquals(segment.attrs.cssClass.asInstanceOf[String].split(" ").contains("turtle-line--jump"), line.jump)
        }
      verifyCoordinates()
      (graph.ofKind("segment") :+ arc).foreach { obj =>
        obj.fire("over")
        assertEquals(graph.angleLabel, Some((s"${math.abs(180.0 - degrees % 360.0).toInt}°", true)))
        verifyCoordinates()
        obj.fire("out")
        assertEquals(graph.angleLabel.map(_._2), Some(false))
        verifyCoordinates()
      }
    }

  test("dense scenes batch object creation into one final JSXGraph update") {
    val graph = new Graph(batchUpdates = true)
    graph.render(List(cmd("forward", 10), cmd("right", 90), cmd("forward", 10)))
    assertEquals(graph.updateCalls.toList, List(("suspend", 0), ("resume", graph.objects.size)))
    val fallback = new Graph
    fallback.render(List(cmd("forward", 10)))
    assertEquals(fallback.updateCalls.toList, List(("update", fallback.objects.size)))
  }

  test("axis, endpoint and angle labels use internal SVG rendering with the contrasting hover style") {
    val graph = new Graph
    graph.render(List(cmd("forward", 10), cmd("right", 90), cmd("forward", 10)))
    assertEquals(graph.axisLabelDisplay, "internal")
    graph.ofKind("point").foreach { point =>
      assertEquals(point.attrs.label.display.asInstanceOf[String], "internal")
      assertEquals(point.attrs.label.cssClass.asInstanceOf[String], "turtle-hover-label")
    }
    graph.ofKind("curve").head.fire("over")
    val label = graph.ofKind("text").head
    assertEquals((label.attrs.display.asInstanceOf[String], label.attrs.cssClass.asInstanceOf[String]), ("internal", "turtle-hover-label"))
  }

  test("dense repeating patterns retain all lines with representative angle overlays") {
    val graph = new Graph(batchUpdates = true)
    val polygon = List.fill(180)(List(cmd("forward", 3), cmd("right", 2))).flatten
    graph.render(polygon)
    assertEquals(graph.ofKind("segment").size, 180)
    assertEquals(graph.ofKind("curve").size, 1)
  }
