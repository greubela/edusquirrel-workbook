package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.graphic.spritemap.{EvaSpriteMap, SpriteMapResourceIdentifier}
import it.evadid.evacuation.core.io.instances.eva
import it.evadid.evacuation.core.io.instances.eva.config.{DefaultMetaConfig, SpriteMapMetaConfig, TopDownMetaConfig}
import it.evadid.evacuation.core.io.util.ResourceReader

import scala.concurrent.{ExecutionContextExecutor, Future}

object SpriteMapResourceLoader {

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global


  def loadSpriteMap(id: SpriteMapResourceIdentifier, resourceName: String, config: SpriteMapMetaConfig)(implicit resourceReader: ResourceReader): Future[EvaSpriteMap] = {
    val lines = resourceReader.getResourceLines(resourceName)
    val res = lines.map(eva.SpriteMapConfigDecoder(id, config).decode)
    res
    //SpriteMapConfigDecoder.decode(lines)
  }


  def loadSpriteMap(id: SpriteMapResourceIdentifier)(implicit reader: ResourceReader): Future[EvaSpriteMap] = {
    id.layout.toLowerCase match {
      case "topdown" => SpriteMapResourceLoader.loadSpriteMap(id, "defs/2022-04-26-TopdownTilemap.txt", TopDownMetaConfig)(using reader)
      case "default" => SpriteMapResourceLoader.loadSpriteMap(id, "defs/2022-04-15-NewDefaultTilemap.txt", DefaultMetaConfig)(using reader)
    }

  }


  /*

    def loadWithSize(spriteMap: EvaSpriteMap, destSize: Int): Option[Future[EvaSpriteMap]] = {
      if (spriteMap.name == "default") {
        Some(loadDefault(destSize))
      } else {
        System.out.println("No smaller SpriteMap found für '" + spriteMap + "' (" + spriteMap.spriteSize + "px)!")
        None
      }
    }

    def getAvailableSizes(spriteMap: EvaSpriteMap): Set[Int] = {
      val res = new mutable.HashSet[Int]
      res += spriteMap.spriteSize

      if (spriteMap.name == "default") {
        res += 5
        res += 16
        res += 32
      }

      res.toSet

    }
  */

}
