package todomove.webElementsOld.webElements.svg.builder

import todomove.webElementsOld.webElements.svg.shapes.{DecorationFactory, TestShapeFactoryAccess}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilderCommand.MoveAbs
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
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

  private def assertBuilderRoundTrip(builder: SvgPathBuilder[Double]): Unit = {
    val immutableBuilder = builder match {
      case imm: SvgPathBuilderImmutable[Double] => imm
      case other => fail(s"Unexpected builder implementation: ${other.getClass}")
    }

    val canonical = canonicalAbsoluteString(immutableBuilder)
    val reparsed = parsePath(immutableBuilder.toSvgPathD)
    val reparsedCanonical = canonicalAbsoluteString(reparsed)
    assertEquals(reparsedCanonical, canonical)
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

  test("duck shape from shape factory keeps canonical representation after round trip") {
    val bounds = Bounds(Point(0.0, 0.0), Dimension(125.0, 50.0))
    val builder = TestShapeFactoryAccess.duck(bounds)
    assertBuilderRoundTrip(builder)
  }

  test("literal shape from shape factory keeps canonical representation after round trip") {
    val bounds = Bounds(Point(10.0, 5.0), Dimension(120.0, 80.0))
    val builder = TestShapeFactoryAccess.literal(bounds)
    assertBuilderRoundTrip(builder)
  }

  test("Snap command outline keeps canonical representation after round trip") {
    val bounds = Bounds(Point(0.0, 0.0), Dimension(180.0, 42.0))
    assertBuilderRoundTrip(TestShapeFactoryAccess.snapCommand(bounds))
  }

  test("Snap reporter outlines keep canonical representation after round trip") {
    val bounds = Bounds(Point(4.0, 7.0), Dimension(150.0, 30.0))
    assertBuilderRoundTrip(TestShapeFactoryAccess.snapReporter(bounds, predicate = false))
    assertBuilderRoundTrip(TestShapeFactoryAccess.snapReporter(bounds, predicate = true))
  }

  test("Snap command outline includes C-slot geometry") {
    val bounds = Bounds(Point(0.0, 0.0), Dimension(210.0, 100.0))
    val slot = Bounds(Point(18.0, 30.0), Dimension(180.0, 38.0))
    val path = TestShapeFactoryAccess.snapCommand(bounds, List(slot))
    assertBuilderRoundTrip(path)
    assert(path.toSvgPathD.contains("A 3,3 0 0,0"), path.toSvgPathD)
    assert(path.pathPoints.exists(point => math.abs(point.y - 68.0) < 0.000001), path.toSvgPathD)
  }

  test("Snap if-else outline supports two C slots") {
    val bounds = Bounds(Point(0.0, 0.0), Dimension(240.0, 160.0))
    val ifSlot = Bounds(Point(18.0, 30.0), Dimension(210.0, 42.0))
    val elseSlot = Bounds(Point(18.0, 88.0), Dimension(210.0, 42.0))
    val path = TestShapeFactoryAccess.snapCShape(bounds, List(ifSlot, elseSlot))
    assertBuilderRoundTrip(path)
    val anticlockwiseSlotArcs = "A 3,3 0 0,0".r.findAllIn(path.toSvgPathD).length
    assertEquals(anticlockwiseSlotArcs, 6)
  }

  test("Snap hat uses the original circular arc and cubic crown") {
    val bounds = Bounds(Point(0.0, 0.0), Dimension(180.0, 48.0))
    val path = TestShapeFactoryAccess.snapHat(bounds)
    assertBuilderRoundTrip(path)
    assert(path.toSvgPathD.contains("A 57.041666666666664,57.041666666666664"), path.toSvgPathD)
    assert(path.toSvgPathD.contains("C 70 0 70 12 119 12"), path.toSvgPathD)
  }

  test("data arrow left from decoration factory keeps canonical representation after round trip") {
    val config = BeRenderingConfig.default()
    val decorationFactory = DecorationFactory[Double](config)
    val builder = decorationFactory.dataArrowLeft(Point(32.0, 24.0))
    assertBuilderRoundTrip(builder)
  }

  test("data arrow right from decoration factory keeps canonical representation after round trip") {
    val config = BeRenderingConfig.default()
    val decorationFactory = DecorationFactory[Double](config)
    val builder = decorationFactory.dataArrowRight(Point(12.0, 18.0))
    assertBuilderRoundTrip(builder)
  }
}
