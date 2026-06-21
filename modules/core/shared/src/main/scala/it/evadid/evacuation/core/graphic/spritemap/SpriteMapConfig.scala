package it.evadid.evacuation.core.graphic.spritemap

import it.evadid.core.datastructures.matrix.Direction
import it.evadid.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMapConfig.parseSpriteMapAnimLine
import it.evadid.evacuation.core.graphic.sprites._
import it.evadid.evacuation.core.graphic.sprites.traits.Sprite
import it.evadid.evacuation.core.io.instances.eva.config.SpriteMapMetaConfig


case class SpriteMapConfig(formatLines: Seq[String], config: SpriteMapMetaConfig) {

  val linesCleaned: Seq[String] = formatLines.map(_.trim()).filterNot(_.isEmpty).filterNot(_ startsWith "#").filterNot(_ startsWith "//")
  val allSpriteLines: Seq[String] = linesCleaned.filterNot(_ contains "=")

  private val notAnimLines = allSpriteLines.filter(!_.startsWith("a"))
  val basicSprites: List[Sprite] = notAnimLines.flatMap(SpriteMapConfig.parseSpriteMapLine(_, config)).toList
  val personSpritesSingle: List[Sprite] = SpriteMapConfig.parsePersonSpriteSingle(allSpriteLines.flatMap(line => parseSpriteMapAnimLine(line, config).toList).toList)
  val personSprites: List[AnimatedPersonSprite] = SpriteMapConfig.parsePersonSprites(allSpriteLines.toList, config).toList

  val sprites: List[Sprite] = basicSprites ++ personSprites
  val allImages: List[Sprite] = basicSprites ++ personSpritesSingle

  val varLines: Seq[String] = linesCleaned.filter(_ contains "=")

  def getVariable(name: String): Option[String] = varLines.find(_ startsWith (name + "=")).map(_.split("=")(1))

  def getIntVariable(name: String): Option[Int] = getVariable(name).map(_ replaceAll("\\D+", "")).map(Integer.parseInt)

  private val columns: Option[Int] = getIntVariable("cols")
  private val rows: Option[Int] = getIntVariable("rows")

  def getShowDimension(): Option[MatrixDimension] =
    if (columns.isDefined && rows.isDefined) Some(new MatrixDimension(columns.get, rows.get, false)) else None

  assert(columns.isDefined && rows.isDefined, "Variables cols and rows must be defined in SpriteMapConfig!")

}

object SpriteMapConfig {


  private def parsePersonSprites(allLines: List[String], config: SpriteMapMetaConfig): List[AnimatedPersonSprite] = {
    val animInfo = allLines.flatMap(line => parseSpriteMapAnimLine(line, config))
    val persons = getPersonSprites(animInfo)
    persons.toList
  }

  private def getPersonSprites(list: List[PersonSpriteInfo]): List[AnimatedPersonSprite] = {

    def getDirSprites(pName: String, pDir: Direction): List[FrameData] = {
      list.filter(_.name == pName).filter(_.direction == pDir).sortBy(_.frameNr).map(_.frameData).toList
    }

    def readMooreDirectionSpritesOrCombine(pName: String, pDir: Direction, altList: List[FrameData]): List[FrameData] = {
      val res = getDirSprites(pName, pDir)
      if (res.nonEmpty) res else altList
    }

    val spriteNames = list.map(_.name).distinct
    val spriteList = spriteNames.map(spriteName => {

      val idVal = list.filter(_.name == spriteName).filter(_.id > 0).minBy(_.id).id

      val upSprites: List[FrameData] = getDirSprites(spriteName, Direction.TOP)
      val leftSprites: List[FrameData] = getDirSprites(spriteName, Direction.LEFT)
      val rightSprites: List[FrameData] = getDirSprites(spriteName, Direction.RIGHT)
      val downSprites: List[FrameData] = getDirSprites(spriteName, Direction.BOTTOM)

      val frameMap: Map[Direction, List[FrameData]] = Map(
        Direction.LEFT -> leftSprites,
        Direction.RIGHT -> rightSprites,
        Direction.TOP -> upSprites,
        Direction.BOTTOM -> downSprites,

        Direction.BOTTOM_RIGHT -> readMooreDirectionSpritesOrCombine(spriteName, Direction.BOTTOM_RIGHT, downSprites ++ rightSprites),
        Direction.BOTTOM_LEFT -> readMooreDirectionSpritesOrCombine(spriteName, Direction.BOTTOM_LEFT, downSprites ++ leftSprites),
        Direction.TOP_RIGHT -> readMooreDirectionSpritesOrCombine(spriteName, Direction.TOP_RIGHT, upSprites ++ rightSprites),
        Direction.TOP_LEFT -> readMooreDirectionSpritesOrCombine(spriteName, Direction.TOP_LEFT, upSprites ++ leftSprites)
      )

      new AnimatedPersonSprite(idVal, spriteName, frameMap)

    })

    spriteList

  }

  private case class PersonSpriteInfo(name: String, id: Int, frameData: FrameData, frameNr: Int, direction: Direction)

  private def parsePersonSpriteSingle(list: List[PersonSpriteInfo]): List[Sprite] = {
    list.map(info => BasicSprite(info.id, info.name, info.frameData))
  }

  private def parseSpriteMapAnimLine(lineString: String, config: SpriteMapMetaConfig): Option[PersonSpriteInfo] = {

    val parts: Array[String] = lineString.split("\\s")

    if (lineString.charAt(0) != 'p' && lineString.charAt(0) != 'a' || parts.length < 6) None else try {

      val col = Integer.parseInt(parts(1))
      val row = Integer.parseInt(parts(2))

      val spriteName = parts(3).trim
      val fileName = parts(3).trim() + "_" + parts(4).trim() + "_" + parts(5).trim()
      val dir = Direction.fromString(parts(4).trim).get
      val frameNr = Integer.parseInt(parts(5).trim)

      val id = config.positionToId(MatrixPosition(col, row))

      Some(PersonSpriteInfo(spriteName, id, FrameData.fromTilemapString(fileName), frameNr, dir))

    } catch {
      case e: Exception =>
        e.printStackTrace()
        None
    }

  }

  private def parseSpriteMapLine(lineString: String, config: SpriteMapMetaConfig): Option[Sprite] = {

    val parts: Array[String] = lineString.split("\\s")

    try {
      println("parse String: " + lineString)
      assert(parts.length > 3, "Too few cols (" + parts.length + ") in spriteMapLine: '" + lineString + "'")

      val col = Integer.parseInt(parts(1))
      val row = Integer.parseInt(parts(2))

      val id: Int = config.positionToId(MatrixPosition(col, row))
      val fileName = parts(parts.length - 1).trim()


      parts(0).charAt(0) match {

        case 't' =>
          val prop = FloorSpriteProperties(parts(3)).get
          Some(new BasicFloorSprite(id, fileName, FrameData.fromTilemapString(fileName), prop, false))
        case 's' =>
          Some(new BasicFloorSprite(id, fileName, FrameData.fromTilemapString(fileName), FloorSpriteProperties.open, true))
        case 'o' =>
          Some(new BasicOverlaySprite(id, fileName, FrameData.fromTilemapString(fileName), 255))
        case 'p' =>
          if (parts.length != 4) None else Some(new BasicPersonSprite(id, fileName, FrameData.fromTilemapString(fileName)))
        case 'a' =>
          None
        case _ =>
          ???

      }


    } catch {
      case e: Exception =>
        println("error at line: " + lineString)
        e.printStackTrace()
        None
    }
  }


}


