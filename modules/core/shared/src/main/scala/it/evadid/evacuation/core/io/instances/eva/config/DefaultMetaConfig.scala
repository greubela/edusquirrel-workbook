package it.evadid.evacuation.core.io.instances.eva.config

import it.evadid.evacuation.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, Sprite}

object DefaultMetaConfig extends SpriteMapMetaConfig {

  private val encodingDim = MatrixDimension(16, 16)

  override def positionToId(matrixPosition: MatrixPosition): Int = {
    val pim = matrixPosition in encodingDim
    assert(pim.isInRange, "SpriteMap IO at the moment supports max. 16x16 (256) tiles (DefaultMetaConfig)!")
    pim.asIndex.get
  }

  override def selectorSpriteAtPosition(matrixPosition: MatrixPosition, spriteMap: SpriteMap): Sprite = {
    val index = (matrixPosition in encodingDim)

    if (index.isInRange && spriteMap.sprites.exists(_.id == index.asIndex.get)) {
      spriteMap.sprites.find(_.id == index.asIndex.get).get
    } else {
      getEmptySprite(spriteMap.sprites)
    }
  }

  override def spriteMapSourceDimension: MatrixDimension = {
    MatrixDimension(9, 9)
  }

  override def getEmptySprite(sprites: Seq[Sprite]): FloorSprite = {

    val emptySprite: Option[Sprite] = sprites.find(_.name == "empty")
    assert(emptySprite.isDefined && emptySprite.get.isInstanceOf[FloorSprite], "No (empty) FloorSprite found with name 'empty'")
    val emptyFloor = emptySprite.get.asInstanceOf[FloorSprite]
    emptyFloor

  }

  // override def directionSpriteName(dir: Direction): String = "arrow_" + dir.name

 // override def personSpriteNames(): List[String] = List("person_male", "person_female", "person_robot")
}
