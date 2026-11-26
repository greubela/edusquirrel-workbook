package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.graphic.spritemap
import it.evadid.evacuation.core.graphic.spritemap.{EvaSpriteMap, SpriteMapConfig, SpriteMapResourceIdentifier}
import it.evadid.evacuation.core.io.instances.eva.config.SpriteMapMetaConfig
import it.evadid.evacuation.core.io.traits.encoder.Decoder

case class SpriteMapConfigDecoder(id: SpriteMapResourceIdentifier, config: SpriteMapMetaConfig) extends Decoder[EvaSpriteMap, List[String]] {

  override def decode(in: List[String]): EvaSpriteMap = {
    val spriteMapConfig: SpriteMapConfig = SpriteMapConfig(in, config)

    /*
    assert(spriteMapConfig.columns.isDefined && spriteMapConfig.rows.isDefined, "Variables columns and rows must be defined!")
    assert(spriteMapConfig.columns.get <= 16 && spriteMapConfig.rows.get <= 16, "SpriteMap IO at the moment supports max. 16x16 (256) tiles (id calculation)")
*/

    spritemap.EvaSpriteMap(id, spriteMapConfig.sprites, config.getEmptySprite(spriteMapConfig.sprites), config, spriteMapConfig.getShowDimension().get)
  }

}
