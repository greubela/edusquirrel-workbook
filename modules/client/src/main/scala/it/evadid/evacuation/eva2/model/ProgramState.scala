package it.evadid.evacuation.eva2.model

import it.evadid.evacuation.core.datastructures.utility.ObservableVar
import it.evadid.evacuation.core.graphic.spritemap.{EvaSpriteMap, SpriteMapResourceIdentifier}
import it.evadid.evacuation.eva2.configuration.{Eva2Config, Eva2GraphicConfig}
import it.evadid.evacuation.eva2.control.Eva2Control
import it.evadid.evacuation.eva2.io.{FloorMapTransformer, ScenarioCache, ServerResourceReader}

import scala.concurrent.ExecutionContextExecutor

case class ProgramState(cache: ScenarioCache, spriteMap: ObservableVar[EvaSpriteMap], floorMap: ObservableVar[EvaFloorMap], config: Eva2Config, graphicConfig: Eva2GraphicConfig) {

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def setScenario(sid: SpriteMapResourceIdentifier, sm: EvaSpriteMap, fm: EvaFloorMap): Unit = {

    graphicConfig.spriteMapProperty.setValue(sid)

    spriteMap.setValue(sm)
    floorMap.setValue(fm)
    Eva2Control.adjustSpriteSize()
    Eva2Control.reload()
  }

  def setScenario(scenarioString: String): Unit = {
    val (sidO, spriteMapF, floorMapF) = cache.loadScenario(scenarioString)
    spriteMapF.onComplete(sm => floorMapF.onComplete(fm => sidO.foreach(sid => {
      setScenario(sid, sm.get, fm.get)
    })))
  }

  def changeSpriteMap(id: SpriteMapResourceIdentifier): Unit = {
    val sm = cache.loadSpriteMap(id)
    sm.onComplete(newSpriteMap =>
      if (id.layout == spriteMap.currentValue.id.layout) spriteMap.setValue(newSpriteMap.get)
      else {
        val newFloor = FloorMapTransformer.basicTransform(spriteMap.currentValue, newSpriteMap.get, floorMap.currentValue)
        setScenario(id, newSpriteMap.get, newFloor)
      })
  }

}

object ProgramState {

  val instance: ProgramState = new ProgramState(new ScenarioCache(ServerResourceReader), new ObservableVar[EvaSpriteMap](), new ObservableVar[EvaFloorMap](), new Eva2Config(), new Eva2GraphicConfig())

}
