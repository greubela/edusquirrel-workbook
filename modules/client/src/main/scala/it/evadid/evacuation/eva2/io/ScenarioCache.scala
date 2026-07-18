package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.graphic.spritemap.{EvaSpriteMap, SpriteMap, SpriteMapResourceIdentifier}
import it.evadid.evacuation.core.io.util.{LocalResourceReader, ResourceReader}
import it.evadid.evacuation.eva2.model.EvaFloorMap

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScenarioCache(resourceReader: ResourceReader) {

  private val scenarioMap = new mutable.HashMap[(String, SpriteMap), EvaFloorMap]()
  private val spritesMap = new mutable.HashMap[SpriteMapResourceIdentifier, EvaSpriteMap]()


  def loadScenario(str: String): (Option[SpriteMapResourceIdentifier], Future[EvaSpriteMap], Future[EvaFloorMap]) = {
    val parts = str.split(":")
    val sid = SpriteMapResourceIdentifier.getFrom(parts(0))
    assert(parts.length == 3, "String '" + str + "' is not formatted correctly, should be spriteMap:tiles:persons (but has " + str.length + " parts)")

    val failFormat = Future.failed(new IllegalArgumentException("[ScenarioCache::loadScenario] String '" + str + "' is not formatted correctly, should be spriteMap:tiles:persons (but has " + str.length + " parts"))
    val failSid = Future.failed(new IllegalArgumentException("[ScenarioCache::loadScenario] Cannot load spriteMap '" + parts(0) + "'"))
    val failFloorBecauseSid = Future.failed(new IllegalArgumentException("[ScenarioCache::loadScenario] Cannot load floorMap because of missing spriteMap"))
    if (parts.length != 3) {
      (Option.empty, failFormat, failFormat)
    } else if (sid.isEmpty) {
      (Option.empty, failSid, failFloorBecauseSid)
    } else {
      val spriteMap: Future[EvaSpriteMap] = loadSpriteMap(sid.get)
      val floorMap: Future[EvaFloorMap] = spriteMap.flatMap(spriteMap => loadFloorMap(str, spriteMap))
      (sid, spriteMap, floorMap)
    }


  }

  def loadSpriteMap(id: SpriteMapResourceIdentifier): Future[EvaSpriteMap] = if (!spritesMap.contains(id)) {
    val spriteMap = SpriteMapResourceLoader.loadSpriteMap(id)(using resourceReader)
    spriteMap.onComplete(map => spritesMap.put(id, map.get))
    spriteMap
  } else Future {
    spritesMap(id)
  }

  def loadFloorMap(str: String, spriteMap: EvaSpriteMap): Future[EvaFloorMap] = Future {
    val tup = (str, spriteMap)
    if (scenarioMap.contains(tup)) {
      scenarioMap(tup)
    } else {
      val floorMap = FloorMapIO(spriteMap).decode(str)
      scenarioMap.put(tup, floorMap)
      floorMap
    }
  }

  def loadSpriteMapByName(str: String, size: Int): Future[EvaSpriteMap] = loadSpriteMap(SpriteMapResourceIdentifier.getFrom(str, size).get)

}

object ScenarioCache {

  val localInstance = new ScenarioCache(LocalResourceReader)
  val instance = new ScenarioCache(ServerResourceReader)

}
