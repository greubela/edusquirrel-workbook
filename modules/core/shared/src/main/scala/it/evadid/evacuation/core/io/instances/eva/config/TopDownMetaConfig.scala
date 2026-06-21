package it.evadid.evacuation.core.io.instances.eva.config

import it.evadid.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, Sprite}

object TopDownMetaConfig extends SpriteMapMetaConfig {


  private val encodingDim = MatrixDimension(7, 50)

  override def positionToId(matrixPosition: MatrixPosition): Int = {
    val pim = matrixPosition in encodingDim
    assert(pim.isInRange, "SpriteMap IO at the moment supports max. 7x36 (252) tiles (DefaultMetaConfig). Invalid positioin: " + matrixPosition + "!")
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

  override def spriteMapSourceDimension: MatrixDimension = MatrixDimension(7, 36)

  override def getEmptySprite(sprites: Seq[Sprite]): FloorSprite = {
    val emptySprite: Option[Sprite] = sprites.find(_.name == "tile_indoors_a")
    assert(emptySprite.isDefined && emptySprite.get.isInstanceOf[FloorSprite], "No (empty) FloorSprite found with name 'singleempty'")
    val emptyFloor = emptySprite.get.asInstanceOf[FloorSprite]
    emptyFloor
  }

}
