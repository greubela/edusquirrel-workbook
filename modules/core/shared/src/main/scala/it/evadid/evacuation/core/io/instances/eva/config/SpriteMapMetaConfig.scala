package it.evadid.evacuation.core.io.instances.eva.config

import it.evadid.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, Sprite}

trait SpriteMapMetaConfig {

  def positionToId(matrixPosition: MatrixPosition): Int

  def selectorSpriteAtPosition(matrixPosition: MatrixPosition, spriteMap: SpriteMap): Sprite

  def spriteMapSourceDimension: MatrixDimension

  def getEmptySprite(sprites: Seq[Sprite]): FloorSprite

}
