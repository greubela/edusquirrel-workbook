package it.evadid.evacuation.eva2.configuration

import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.property.discrete.{BasicDiscreteConfigProperty, BasicDiscreteObservableConfigProperty, ObservableDiscreteConfigProperty}
import it.evadid.evacuation.config.property.{BasicConfigProperty, ObservableConfigProperty}
import it.evadid.evacuation.config.value.ConfigValue
import it.evadid.evacuation.core.graphic.spritemap.SpriteMapResourceIdentifier
import it.evadid.evacuation.eva2.model.ProgramState
import org.scalajs.dom.document

case class Eva2GraphicConfig() {


  val animationCounter: ObservableConfigProperty[Long] = ObservableConfigProperty.fromLongProperty(BasicConfigProperty[Long]("animTick", 1, "Animation Ticker"))

  val spriteMapProperty : ObservableDiscreteConfigProperty[SpriteMapResourceIdentifier] = {

    val configValues = SpriteMapResourceIdentifier.availableSpriteMaps.map(spriteObj => ConfigValue(spriteObj, spriteObj.id, Some(spriteObj.description)))
    val spriteMapProperty = BasicDiscreteObservableConfigProperty(new BasicDiscreteConfigProperty("graphics", configValues, configValues.find(_.value.id == "default16").get, Some("SpriteMap")))

    val smlListener: PropertyListener[SpriteMapResourceIdentifier] = (prop, oldVal, newVal) => ProgramState.instance.changeSpriteMap(newVal.value)
    spriteMapProperty.listener += smlListener

    spriteMapProperty
  }

  val vwWidth: ObservableConfigProperty[Int] = ObservableConfigProperty.fromIntProperty(BasicConfigProperty[Int]("vwWidth", 800, "ViewPort Width"))
  val vwHeight: ObservableConfigProperty[Int] = ObservableConfigProperty.fromIntProperty(BasicConfigProperty[Int]("vwWidth", 600, "ViewPort Height"))


  def updateCSSVariables(): Unit = {

    val cssValue = "--nrTabs: 5;\n" +
      "--tileSize: " + spriteMapProperty.getValue.value.size + "px;\n" +
      "--manualCols: " + spriteMapProperty.getValue.value.desiredColsInSelection + ";\n"

    document.body.style = cssValue

  }


}
