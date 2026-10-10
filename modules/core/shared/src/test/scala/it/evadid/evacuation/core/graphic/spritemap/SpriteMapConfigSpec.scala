package it.evadid.evacuation.core.graphic.spritemap

import it.evadid.core.datastructures.matrix.{Direction, MatrixDimension}
import it.evadid.evacuation.core.io.instances.eva.config.{DefaultMetaConfig, TopDownMetaConfig}
import it.evadid.evacuation.core.graphic.sprites.*
import munit.FunSuite

class SpriteMapConfigSpec extends FunSuite {
  private def config(lines: String*): SpriteMapConfig = SpriteMapConfig(Seq("cols=9", "rows=9") ++ lines, DefaultMetaConfig)
  test("blank lines and both comment styles are ignored") {
    val parsed = config("", "  ", " # comment", " // comment", "s 1 1 exit")
    assertEquals(parsed.basicSprites.size, 1)
    assertEquals(parsed.getShowDimension(), Some(MatrixDimension(9, 9)))
  }
  test("variables allow surrounding spaces and preserve their entire value") {
    val parsed = SpriteMapConfig(Seq(" cols = 9 ", " rows = 8 ", "title = Room=One", "empty="), DefaultMetaConfig)
    assertEquals(parsed.getShowDimension(), Some(MatrixDimension(9, 8)))
    assertEquals(parsed.getVariable("title"), Some("Room=One"))
    assertEquals(parsed.getVariable("empty"), Some(""))
    assertEquals(parsed.getVariable("missing"), None)
  }
  test("non-numeric variable values return None rather than extracting digits") {
    val parsed = config("speed=fast12", "empty=", "negative=-2", "large=99999999999999")
    assertEquals(parsed.getIntVariable("speed"), None)
    assertEquals(parsed.getIntVariable("empty"), None)
    assertEquals(parsed.getIntVariable("large"), None)
    assertEquals(parsed.getIntVariable("negative"), Some(-2))
  }
  test("missing and invalid dimensions are rejected explicitly") {
    for (lines <- List(Seq("rows=9"), Seq("cols=9"), Seq("cols=0", "rows=9"), Seq("cols=-2", "rows=9"), Seq("cols=text", "rows=9"))) {
      intercept[IllegalArgumentException](SpriteMapConfig(lines, DefaultMetaConfig))
    }
  }
  test("basic floor, safe, overlay and person lines preserve their properties") {
    val parsed = config("t 1 1 10101010 floor", "s 2 1 exit", "o 3 1 tree", "p 4 1 person")
    val floor = parsed.basicSprites(0).asInstanceOf[BasicFloorSprite]
    assertEquals(floor.properties, FloorSpriteProperties("10101010").get)
    assert(!floor.isSave)
    assert(parsed.basicSprites(1).asInstanceOf[BasicFloorSprite].isSave)
    assertEquals(parsed.basicSprites(2).asInstanceOf[BasicOverlaySprite].opacityUpTo255, 255)
    assertEquals(parsed.basicSprites(3).frameData, FrameData("person"))
  }
  test("repeated spaces and tabs tokenize like single spaces") {
    val parsed = config("t  1\t1  11111111\tfloor", "a  1\t2 person\tbottom 0")
    assertEquals(parsed.basicSprites.size, 1)
    assertEquals(parsed.personSprites.size, 1)
    assertEquals(parsed.personSprites.head.frameData, FrameData("person_bottom_0"))
  }
  test("malformed, unknown and out-of-range sprite lines are skipped") {
    val parsed = config("x 1 1 unknown", "t", "t 1 1 bad floor", "s -1 0 invalid", "s 999 0 invalid", "a 1 1 person unknown 0", "s 2 1 valid")
    assertEquals(parsed.sprites.map(_.name), List("valid"))
  }
  test("a sprite at atlas position zero is a valid animation") {
    val parsed = config("a 0 0 person bottom 0")
    assertEquals(parsed.personSprites.head.id, 0)
    assertEquals(parsed.personSprites.head.frameData, FrameData("person_bottom_0"))
  }
  test("animation frames are sorted numerically and diagonal fallbacks preserve order") {
    val parsed = config("a 1 1 person bottom 2", "a 2 1 person bottom 0", "a 3 1 person right 0", "a 4 1 person top 0", "a 5 1 person left 0")
    val sprite = parsed.personSprites.head
    assertEquals(sprite.data(Direction.BOTTOM).map(_.filename), List("person_bottom_0", "person_bottom_2"))
    assertEquals(sprite.data(Direction.BOTTOM_RIGHT).map(_.filename), List("person_bottom_0", "person_bottom_2", "person_right_0"))
    assertEquals(parsed.allImages.size, 5)
  }
  test("explicit diagonal frames take precedence over cardinal fallbacks") {
    val parsed = config("a 1 1 person bottom 0", "a 2 1 person right 0", "a 3 1 person bottomright 0")
    assertEquals(parsed.personSprites.head.data(Direction.BOTTOM_RIGHT), List(FrameData("person_bottomright_0")))
  }
  test("animation names stay separate and static people remain static") {
    val parsed = config("a 1 1 first bottom 0", "a 2 1 second bottom 0", "p 3 1 static")
    assertEquals(parsed.personSprites.map(_.name), List("first", "second"))
    assertEquals(parsed.basicSprites.map(_.name), List("static"))
  }
  test("top-down layout uses its own atlas coordinates") {
    val parsed = SpriteMapConfig(Seq("cols=7", "rows=36", "s 2 3 exit"), TopDownMetaConfig)
    assertEquals(parsed.basicSprites.head.id, 23)
  }
}
