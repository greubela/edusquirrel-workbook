package it.evadid.evacuation.eva2.graphic

import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{OverlaySprite, Sprite}
import it.evadid.evacuation.eva2.model.ProgramState
import it.evadid.evacuation.html.model.WebImageConfig
import org.scalajs.dom.{DragEvent, MouseEvent}

object ImageConfigFactory {

  def forSimulationNavigationButtonOuter(id: String, name: String, onClick: MouseEvent => Any): WebImageConfig = {
    WebImageConfig(id, "./img/icons/player/" + name + ".svg", name, Map("click" -> onClick), Map(), Map(), true)
  }

  def forSimulationNavigationButton(id: String, name: String, onClick: MouseEvent => Any): WebImageConfig = {
    WebImageConfig(id, "./img/icons/player/" + name + ".svg", name, Map("click" -> onClick), Map(), Map("class" -> "sim-nav-button"), true)
  }

  def forExtensionButton(id: String, name: String, onClick: MouseEvent => Any): WebImageConfig = {
    val size = ProgramState.spriteMap.spriteSize
    WebImageConfig(id, "./img/icons/" + name + ".svg", name, Map("click" -> onClick), Map(), Map("class" -> "map-editor-control-button"), true)
  }

  /*def forEditorControlSelection(name: String, onClick: MouseEvent => Any): ImageConfig = {
    println("forEditorControlSelection(" + name + ", " + onClick + ")")
    ImageConfig("map-editor-control-button", "./img/tiles/" + name + ".png", name, Map("click" -> onClick), Map())
  }*/

  def firstFrameForTile(sprite: Sprite, spriteMap: SpriteMap): WebImageConfig = {
    val mouseEvents: Map[String, MouseEvent => Any] = Map()
    val dragEvents: Map[String, DragEvent => Any] = Map()
    WebImageConfig("tileImg", spriteMap.getFullSpritePath(sprite.frameData), sprite.toString, mouseEvents, dragEvents, Map())
  }

  def forOverlay(overlay: Sprite, spriteMap: SpriteMap, zIndex: Int = 100): WebImageConfig = {

    val op = overlay match {
      case sprite: OverlaySprite => "opacity:" + (sprite.opacityUpTo255 / 255.0) + ";"
      case _ => ""
    }
    val styleVal = "grid-column:1;grid-row:1;z-index:" + zIndex + ";" + op

    WebImageConfig("tileOverlayImg", spriteMap.getFullSpritePath(overlay.frameData), overlay.name, Map(), Map(), Map("style" -> styleVal))
  }

}
