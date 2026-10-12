package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import munit.FunSuite

class SvgPathParserCoverageSpec extends FunSuite {
  private def parse(input: String) = SvgPathParser.parseString(input).getOrElse(fail(s"Cannot parse $input"))

  test("absolute and relative moves allow repeated coordinate pairs as lines") {
    assertEquals(parse("M 10,20 30,40 50,60").current, Point(50.0, 60.0))
    assertEquals(parse("m 10 20 3 4 5 6").current, Point(18.0, 30.0))
    assertEquals(parse("M 10 20 m 2 3 l 4 5").current, Point(16.0, 28.0))
  }

  test("horizontal and vertical commands update only their own axis") {
    val path = parse("M 10 20 H 30 V 40 h -5 v -10 L 7 8 l 1 2")
    assertEquals(path.current, Point(8.0, 10.0))
    assertEquals(path.pathPoints.takeRight(6), List(Point(30.0, 20.0), Point(30.0, 40.0), Point(25.0, 40.0), Point(25.0, 30.0), Point(7.0, 8.0), Point(8.0, 10.0)))
  }

  test("curve commands preserve absolute and relative controls") {
    val path = parse("M 1 2 C 3 4 5 6 7 8 c 1 2 3 4 5 6 Q 20 21 22 23 q 1 2 3 4")
    assertEquals(path.current, Point(25.0, 27.0))
    assertEquals(path.toSvgPathD, "M 1 2 M 1 2 C 3 4 5 6 7 8 c 1 2 3 4 5 6 Q 20 21 22 23 q 1 2 3 4")
  }

  test("arc commands retain radii, rotation and flags") {
    val path = parse("M 1 2 A 3 4 45 0 1 10 20 a 5 6 90 1 0 7 8")
    assertEquals(path.current, Point(17.0, 28.0))
    assert(path.toSvgPathD.contains(" A 3,4 45 0,1 10 20 a 5,6 90 1,0 7 8"))
  }

  test("close path resets relative coordinates to each subpath start") {
    assertEquals(parse("M 10 20 L 30 40 z l 1 2").current, Point(11.0, 22.0))
    assertEquals(parse("M 10 20 M 3 4 L 30 40 Z h 2").current, Point(5.0, 4.0))
  }

  test("compact signed decimal and exponent numbers follow SVG number syntax") {
    assertEquals(parse("M.5-.25L1e2-2.5e-1").current, Point(100.0, -0.25))
    assertEquals(parse("M+1.0,+2E1 L.25.75").current, Point(0.25, 0.75))
  }

  test("empty, unsupported and incomplete paths are rejected") {
    List("", " ", "L 1 2", "1 2", "Z", "M", "M 1", "M 1 2 L", "M 1 2 L 3", "M 0 0 X 1 2", "M 0 0 S 1 2 3 4", "M 0 0 Z 1 2").foreach { input =>
      assertEquals(SvgPathParser.parseString(input), None, input)
    }
  }

  test("an incomplete command cannot be silently replaced by the next command") {
    List("M L 1 2", "M 0 0 L M 1 2", "M 0 0 C Z").foreach { input =>
      assertEquals(SvgPathParser.parseString(input), None, input)
    }
  }

  test("nonfinite and malformed numbers are rejected without throwing") {
    List("M NaN 0", "M Infinity 0", "M 1e999 0", "M 1e 0", "M 0x1 0").foreach { input =>
      assertEquals(SvgPathParser.parseString(input), None, input)
    }
  }

  test("arc flags must be zero or one and radii must be nonnegative") {
    List("M 0 0 A 2 3 0 2 0 1 1", "M 0 0 a 2 3 0 0 -1 1 1", "M 0 0 A -2 3 0 0 0 1 1").foreach { input =>
      assertEquals(SvgPathParser.parseString(input), None, input)
    }
  }
}
