package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.graphic.spritemap.EvaSpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.FloorSprite
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

object FloorMapTransformer {


  def basicTransform(srcMap: EvaSpriteMap, destMap: EvaSpriteMap, floorMatrix: EvaFloorMap): EvaFloorMap = {

    println("--- BASIC TRANSFORM: " + srcMap.id.description + " -> " + destMap.id.description)

    val fullyOpen: List[FloorSprite] = destMap.tiles.filter(_.properties.isFullyOpen()).filter(!_.isSave)
    val fullyClosed: List[FloorSprite] = destMap.tiles.filter(_.properties.isFullyClosed())
    val persons = destMap.persons
    val safe = destMap.tiles.filter(_.isSave)

    val resMap = floorMatrix.floorMatrix.mapTiles(tile =>
      if (tile.isSave) safe.head
      else if (tile.properties.isFullyOpen()) fullyOpen.head
      else fullyClosed.head

    )
    val resPerson = floorMatrix.persons.map(oldP => Person(oldP.id, oldP.pos, persons.head))

    EvaFloorMap(resMap, resPerson)
  }


}
