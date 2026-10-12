package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

class SvgPathBuildersSpec extends FunSuite {
  private val start = Point(10.0, 20.0)
  private val factories: List[(String, Point[Double] => SvgPathBuilder[Double])] = List(
    "mutable" -> (p => SvgPathBuilder.mutableBuilder(p)),
    "immutable" -> (p => SvgPathBuilder.immutableBuilder(p))
  )

  factories.foreach { (name, factory) =>
    test(s"$name starts at its initial point and tracks moves and lines") {
      val initial = factory(start)
      assertEquals(initial.current, start)
      assertEquals(initial.toSvgPathD, "M 10 20")
      val path = initial.moveToRel(Dimension(2.0, 3.0)).lineToAbs(Point(1.0, 2.0))
        .horizontalLineWithWidth(4.0).verticalLineWithHeight(-1.0).lineToRel(Dimension(1.0, 1.0))
      assertEquals(path.current, Point(6.0, 2.0))
      assertEquals(path.pathPoints, List(start, Point(12.0, 23.0), Point(1.0, 2.0), Point(5.0, 2.0), Point(5.0, 1.0), Point(6.0, 2.0)))
      assertEquals(path.bounds, Bounds(Point(1.0, 1.0), Dimension(11.0, 22.0)))
      assertEquals(path.requiresDimension, Dimension(11.0, 22.0))
    }

    test(s"$name converts relative curve control points from the preceding endpoint") {
      val path = factory(start).cubicBezierToRel(Dimension(1.0, 2.0), Dimension(3.0, 4.0), Dimension(5.0, 6.0))
        .quadraticBezierWithRel(Dimension(2.0, 3.0), Dimension(4.0, 5.0))
      assertEquals(path.pathPoints, List(start, Point(15.0, 26.0), Point(19.0, 31.0)))
      val expected = if (name == "mutable") "M 10 20 C 11 22 13 24 15 26 Q 17 29 19 31"
        else "M 10 20 c 1 2 3 4 5 6 q 2 3 4 5"
      assertEquals(path.toSvgPathD, expected)
    }

    test(s"$name encodes absolute curves and both arc flags") {
      val path = factory(start).cubicBezierToAbs(Point(1.0, 2.0), Point(3.0, 4.0), Point(5.0, 6.0))
        .quadraticBezierToAbs(Point(7.0, 8.0), Point(9.0, 10.0))
        .arcToAbs(2.0, 3.0, 45.0, false, true, Point(11.0, 12.0))
        .arcToAbs(4.0, 5.0, 0.0, true, false, Point(13.0, 14.0))
      assertEquals(path.toSvgPathD, "M 10 20 C 1 2 3 4 5 6 Q 7 8 9 10 A 2,3 45 0,1 11 12 A 4,5 0 1,0 13 14")
      assertEquals(path.current, Point(13.0, 14.0))
    }

    test(s"$name closes to the latest subpath start before a relative segment") {
      val path = factory(start).lineToAbs(Point(30.0, 40.0)).closePath()
      assertEquals(path.current, start)
      val next = path.moveToAbs(Point(3.0, 4.0)).lineToRel(Dimension(9.0, 8.0))
        .closePath().lineToRel(Dimension(1.0, 2.0))
      assertEquals(next.current, Point(4.0, 6.0))
      assert(next.toSvgPathD.contains(" Z"))
    }

    test(s"$name circle and directional arcs preserve their intended endpoints") {
      val circle = factory(start).markSpot(2.0)
      assertEquals(circle.current, start)
      assertEquals(circle.pathPoints.take(5), List(start, Point(8.0, 20.0), Point(12.0, 20.0), Point(8.0, 20.0), start))
      val arcs = factory(start).addArcToTheTopMoveRight(2.0).addArcToTheRightMoveBottom(3.0)
      assertEquals(arcs.current, Point(14.0, 26.0))
    }

    test(s"$name draws rectangles and connectors in either direction") {
      val rectangle = factory(start).drawBoundRectangle(Bounds(start, Dimension(4.0, 6.0)))
      assertEquals(rectangle.current, start)
      for (inverted <- List(false, true)) {
        val connector = factory(start).addControlFlowConnector(2.0, inverted)
        assertEquals(connector.current, Point(22.0, 20.0))
        assertEquals(connector.pathPoints(2), Point(14.0, if (inverted) 18.0 else 22.0))
      }
      for (height <- List(10.0, 30.0)) {
        val bracket = factory(start).addCommandBracketDown(5.0, height)
        assertEquals(bracket.current, Point(10.0, 20.0 + math.max(10.0, height)))
      }
    }
  }

  test("mutable bounds follow edits even when read before the edit") {
    val path = SvgPathBuilder.mutableBuilder(start)
    assertEquals(path.requiresDimension, Dimension(0.0, 0.0))
    path.lineToAbs(Point(40.0, 60.0))
    assertEquals(path.requiresDimension, Dimension(30.0, 40.0))
  }

  test("immutable translation shifts absolute points and retains relative movements") {
    val original = SvgPathBuilder.immutableBuilder(start)
      .moveToAbs(Point(1.0, 2.0)).lineToAbs(Point(3.0, 4.0))
      .cubicBezierToAbs(Point(5.0, 6.0), Point(7.0, 8.0), Point(9.0, 10.0))
      .quadraticBezierToAbs(Point(11.0, 12.0), Point(13.0, 14.0))
      .arcToAbs(2.0, 3.0, 45.0, true, false, Point(15.0, 16.0))
      .lineToRel(Dimension(1.0, 2.0))
    val moved = original.moveWholePath(Dimension(100.0, -10.0))
    assertEquals(moved.pathPoints, original.pathPoints.map(p => Point(p.x + 100.0, p.y - 10.0)))
    assertEquals(moved.toSvgPathD, "M 110 10 M 101 -8 L 103 -6 C 105 -4 107 -2 109 0 Q 111 2 113 4 A 2,3 45 1,0 115 6 l 1 2")
    assertEquals(original.absStartPoint, start)
  }

  test("command conversions preserve endpoints and curve controls") {
    val commands: List[SvgPathBuilderCommand[Double]] = List(
      MoveAbs(Point(12.0, 23.0)), LineAbs(Point(12.0, 23.0)),
      CubicAbs(Point(11.0, 21.0), Point(13.0, 24.0), Point(12.0, 23.0)),
      QuadAbs(Point(11.0, 21.0), Point(12.0, 23.0)),
      ArcAbs(2.0, 3.0, 45.0, false, true, Point(12.0, 23.0))
    )
    commands.foreach { command =>
      val absolute = command.toAbsoluteCommand(start)
      val relative = command.toRelativeCommand(start)
      assertEquals(relative.relativeMovement, Dimension(2.0, 3.0))
      assertEquals(relative.toAbsoluteCommand(start), absolute)
      assertEquals(relative.toRelativeCommand(start), relative)
      assert(relative.getPathDString().nonEmpty)
    }
    val path = SvgPathBuilderImmutable(StartPathCommand(start), commands)
    assertEquals(path.relativeCommands.size, commands.size)
    assertEquals(path.absoluteCommands.map(_.positionAfterCommand), List.fill(5)(Point(12.0, 23.0)))
    assertEquals(RawAppend[Double](" custom").getPathDString(), " custom")
    assertEquals(RawAppend[Double](" custom").toAbsoluteCommand(start).positionAfterCommand, start)
    assertEquals(CenteredCircleControl(2.0).toRelativeCommand(start).toAbsoluteCommand(start).controlPointsAbsolute, List(Point(12.0, 20.0)))
  }

  test("percentage coordinates scale each axis and report out of bounds values") {
    val logger = BasicLogger()
    val initial = SvgPathBuilderRelativeCoords(logger, SvgPathBuilder(start), Dimension(200.0, 80.0))
    assertEquals(initial.percTansPos(50.0, 25.0), Point(100.0, 20.0))
    assertEquals(initial.intToT(5), 5.0)
    val path = initial.moveToRel(10.0, 20.0).lineToRel(-5.0, 110.0)
      .cubicBezierToRel(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      .quadraticBezierWithRel(7.0, 8.0, 9.0, 10.0)
      .arcToRel(10.0, 20.0, 30.0, 40.0, 45.0, false, true)
    assertEquals(path.baseBuilder.current, Point(108.0, 168.8))
    assert(logger.getOut().contains("negative"))
    assert(logger.getOut().contains("> 100"))
    assertEquals(initial.baseBuilder.current, start)
  }
}
