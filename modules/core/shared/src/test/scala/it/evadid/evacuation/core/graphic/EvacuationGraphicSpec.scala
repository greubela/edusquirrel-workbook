package it.evadid.evacuation.core.graphic

import it.evadid.core.datastructures.matrix.{Direction, MatrixPosition}
import it.evadid.evacuation.core.graphic.sprites.*
import it.evadid.evacuation.core.graphic.model.*
import it.evadid.evacuation.core.graphic.spritemap.*
import munit.FunSuite
import upickle.default.*

class EvacuationGraphicSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): Unit = {
    assertEquals(read[T](write(value)), value)
    assertEquals(readBinary[T](writeBinary(value)), value)
  }
  test("graphic value models have JSON and binary default codecs") {
    roundTrip(EvaColor(23, 45, 67, 89))
    roundTrip(HSBColor(-0.25, 0.5, 0.75))
    roundTrip(EvaFont(12.5, "School Sans", true, true))
    roundTrip(FrameData("floor_↗"))
    roundTrip(SpriteMapResourceIdentifier("custom", "topdown", 32, "学校", Some("assets")))
    roundTrip(FloorSpriteProperties("10101010").get)
  }
  test("file information codecs preserve signed bytes and filenames") {
    val file = EvaFileInformation("floor.eva", Array[Byte](0, -1, 127, -128))
    for (decoded <- List(read[EvaFileInformation](write(file)), readBinary[EvaFileInformation](writeBinary(file)))) {
      assertEquals(decoded.fileName, file.fileName)
      assertEquals(decoded.fileData.toList, file.fileData.toList)
    }
  }
  test("RGB primaries and secondaries have the expected hue") {
    val colors = List(EvaColor(255, 0, 0), EvaColor(255, 255, 0), EvaColor(0, 255, 0), EvaColor(0, 255, 255), EvaColor(0, 0, 255), EvaColor(255, 0, 255))
    colors.zipWithIndex.foreach { (color, index) =>
      assertEqualsDouble(color.toHSB.hue, index / 6.0, 1e-10)
      assertEquals(color.toHSB.toRGB, color)
    }
  }
  test("arbitrary opaque RGB colors survive HSB conversion") {
    for (r <- List(0, 31, 128, 255); g <- List(0, 63, 192, 255); b <- List(0, 47, 160, 255)) {
      val color = EvaColor(r, g, b)
      assertEquals(color.toHSB.toRGB, color)
    }
  }
  test("achromatic HSB colors remain opaque") {
    assertEquals(HSBColor(0.0, 0.0, 0.0).toRGB, EvaColor.black)
    assertEquals(HSBColor(0.3, 0.0, 1.0).toRGB, EvaColor.white)
    assertEquals(HSBColor(0.7, 0.0, 0.5).toRGB, EvaColor(128, 128, 128))
  }
  test("hues wrap in both directions") {
    assertEquals(HSBColor(-1.0 / 6, 1, 1).toRGB, EvaColor(255, 0, 255))
    assertEquals(HSBColor(7.0 / 6, 1, 1).toRGB, EvaColor(255, 255, 0))
  }
  test("invalid HSB saturation and brightness are rejected") {
    for ((s, b) <- List((-0.1, 0.5), (1.1, 0.5), (0.5, -0.1), (0.5, 1.1), (Double.NaN, 0.5))) {
      intercept[IllegalArgumentException](HSBColor(0, s, b).toRGB)
    }
  }
  test("color gradients clamp endpoints and choose the requested hue direction") {
    val start = HSBColor(0.9, 1, 1)
    val end = HSBColor(0.1, 1, 1)
    assertEqualsDouble(EvaColor.getColorGradient2(start, end, -1).hue, 0.9, 1e-6)
    assertEqualsDouble(EvaColor.getColorGradient2(start, end, 2).hue, 0.1, 1e-6)
    assertEqualsDouble(EvaColor.getColorGradient2(start, end, 0.5).hue, 0, 1e-6)
    assertEqualsDouble(EvaColor.getColorGradient2(start, end, 0.5, false).hue, 0.5, 1e-6)
  }
  test("font CSS supports every style combination") {
    for ((bold, italic, prefix) <- List((false, false, ""), (true, false, "bold "), (false, true, "italic "), (true, true, "italic bold "))) {
      assertEquals(EvaFont(12.5, "School", bold, italic).toCSSString, prefix + "12.5px \"School\"")
    }
  }
  test("floor properties accept exactly eight binary flags") {
    for (invalid <- List("", "1111111", "111111111", "1111111x", " 11111111")) assertEquals(FloorSpriteProperties(invalid), None)
    assert(FloorSpriteProperties.open.isFullyOpen())
    assert(!FloorSpriteProperties.open.isOneClosed())
    assert(FloorSpriteProperties.closed.isFullyClosed())
    assert(FloorSpriteProperties.closed.isOneClosed())
  }
  test("every floor flag enables its corresponding neighboring cell") {
    val origin = MatrixPosition(10, 20)
    val deltas = List((0,-1), (-1,-1), (-1,0), (-1,1), (0,1), (1,1), (1,0), (1,-1))
    deltas.zipWithIndex.foreach { (delta, index) =>
      val flags = List.tabulate(8)(i => if (i == index) '1' else '0').mkString
      val properties = FloorSpriteProperties(flags).get
      assertEquals(properties.reachableFrom(origin), Set(origin, MatrixPosition(10 + delta._1, 20 + delta._2)))
      assert(!properties.isFullyClosed())
      assert(!properties.isFullyOpen())
    }
    assertEquals(FloorSpriteProperties.closed.reachableFrom(origin), Set(origin))
    assertEquals(FloorSpriteProperties.open.reachableFrom(origin).size, 9)
  }
  test("sprite identifiers resolve supported layouts and reject unknown combinations") {
    for (id <- SpriteMapResourceIdentifier.availableSpriteMaps) {
      assertEquals(SpriteMapResourceIdentifier.getFrom(id.id), Some(id))
      assertEquals(SpriteMapResourceIdentifier.getFrom(id.layout, id.size), Some(id))
      assertEquals(id.desiredColsInSelection, if (id.layout == "default") 9 else 7)
    }
    for (invalid <- List("", "unknown32", "default", "default999", "topdownx")) assertEquals(SpriteMapResourceIdentifier.getFrom(invalid), None)
  }
  test("concrete sprite codecs preserve frame lists, properties and direction maps") {
    val frame = FrameData("floor")
    roundTrip(BasicSprite(1, "basic", frame))
    roundTrip(BasicPersonSprite(2, "person", frame))
    roundTrip(BasicOverlaySprite(3, "overlay", frame, 127))
    roundTrip(BasicFloorSprite(4, "floor", frame, FloorSpriteProperties.open, true))
    roundTrip(BasicAnimatedSprite(5, "animated", List(frame, FrameData("next"))))
    roundTrip(AnimatedOverlaySprite(6, "animated overlay", List(frame), 128))
    roundTrip(AnimatedPersonSprite(7, "moving", Map(Direction.BOTTOM -> List(frame), Direction.TOP -> List(FrameData("up")))))
  }
  test("animated person frames wrap and retain direction") {
    val down = List(FrameData("d0"), FrameData("d1"))
    val sprite = AnimatedPersonSprite(1, "person", Map(Direction.BOTTOM -> down, Direction.TOP -> List(FrameData("u0"))))
    assertEquals(sprite.frameData, down.head)
    assertEquals(sprite.getFrame(3, Direction.BOTTOM), down(1))
    assertEquals(sprite.getFrame(-1, Direction.BOTTOM), down(1))
    assertEquals(sprite.getFrame(Long.MaxValue, Direction.TOP), FrameData("u0"))
  }

  test("default codec readers retain optional constructor defaults") {
    assertEquals(read[EvaColor]("""{"red":1,"green":2,"blue":3}"""), EvaColor(1, 2, 3, 255))
    assertEquals(read[EvaFont]("""{"sizeInPx":12,"name":"School"}"""), EvaFont(12, "School", false, false))
    assertEquals(read[SpriteMapResourceIdentifier]("""{"id":"custom","layout":"default","size":16,"description":"tiles"}"""),
      SpriteMapResourceIdentifier("custom", "default", 16, "tiles", None))
  }

}
