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

object SpriteMapMetaConfig {
  given upickle.default.ReadWriter[SpriteMapMetaConfig] = upickle.default.readwriter[String].bimap(
    config => config match {
      case DefaultMetaConfig => "default"
      case TopDownMetaConfig => "top-down"
      case other => throw new IllegalArgumentException(s"Unsupported sprite layout: ${other.getClass.getName}")
    }, name => name match {
      case "default" => DefaultMetaConfig
      case "top-down" => TopDownMetaConfig
      case other => throw new IllegalArgumentException(s"Unknown sprite layout: $other")
    })
}
