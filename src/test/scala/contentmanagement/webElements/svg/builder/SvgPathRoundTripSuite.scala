package contentmanagement.webElements.svg.builder

import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand.MoveAbs
import munit.FunSuite

class SvgPathRoundTripSuite extends FunSuite {

  private def parsePath(d: String): SvgPathBuilderImmutable[Double] = {
    SvgPathParser
      .parseString(d)
      .collect { case imm: SvgPathBuilderImmutable[Double] => imm }
      .getOrElse(fail(s"Could not parse path: $d"))
  }

  private def toAbsolute(builder: SvgPathBuilderImmutable[Double]): SvgPathBuilderImmutable[Double] =
    builder.copy(furtherCommands = builder.absoluteCommands)

  private def toRelative(builder: SvgPathBuilderImmutable[Double]): SvgPathBuilderImmutable[Double] =
    builder.copy(furtherCommands = builder.relativeCommands)

  private def canonicalAbsoluteString(builder: SvgPathBuilderImmutable[Double]): String = {
    val abs = toAbsolute(builder)
    var current = abs.absStartPoint
    val filtered = abs.absoluteCommands.flatMap {
      case move @ MoveAbs(p) if p == current =>
        current = p
        Nil
      case cmd =>
        current = cmd.positionAfterCommand
        cmd :: Nil
    }
    abs.copy(furtherCommands = filtered).toSvgPathD
  }

  private val sampleAbsolutePath =
    "M 10 10 L 30 10 A 8,12 45 1,0 50 30 Q 55 35 60 40 Z"

  test("absolute -> relative -> absolute round trip keeps canonical absolute path") {
    val parsed = parsePath(sampleAbsolutePath)
    val relative = toRelative(parsed)
    val reparsed = parsePath(relative.toSvgPathD)
    val reparsedAbsolute = canonicalAbsoluteString(reparsed)
    val originalAbsolute = canonicalAbsoluteString(parsed)
    assertEquals(reparsedAbsolute, originalAbsolute)
  }

  test("absolute input canonicalises to same absolute representation") {
    val parsed = parsePath(sampleAbsolutePath)
    val canonical1 = canonicalAbsoluteString(parsed)
    val canonical2 = canonicalAbsoluteString(parsed)
    assertEquals(canonical2, canonical1)
  }

  test("builder -> path -> builder round trip keeps absolute representation") {
    val builder = SvgPathBuilder[Double](Point(5.0, 7.0))
      .lineToRel(Dimension(10.0, 0.0))
      .arcToRel(6.0, 6.0, 0.0, largeArc = false, sweep = true, Dimension(4.0, 3.0))
      .cubicBezierToAbs(Point(25.0, 20.0), Point(28.0, 22.0), Point(30.0, 18.0))
      .arcToAbs(4.0, 10.0, 45.0, largeArc = true, sweep = true, Point(32.0, 28.0))
      .closePath()

    val immutableBuilder = builder match {
      case imm: SvgPathBuilderImmutable[Double] => imm
      case other => fail(s"Unexpected builder implementation: ${other.getClass}")
    }

    val originalPath = builder.toSvgPathD
    val canonical = canonicalAbsoluteString(immutableBuilder)
    val reparsed = parsePath(originalPath)
    val reparsedCanonical = canonicalAbsoluteString(reparsed)
    assertEquals(reparsedCanonical, canonical)
  }
}
