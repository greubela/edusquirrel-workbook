package it.evadid.evacuation.eva1.control.configuration

import it.evadid.evacuation.config.property.discrete.{BasicDiscreteConfigProperty, BasicDiscreteObservableConfigProperty, ObservableDiscreteConfigProperty}
import it.evadid.evacuation.config.property.{BasicConfigProperty, ObservableConfigProperty}

class Eva1GraphicConfig {

  val restartAnimation: ObservableDiscreteConfigProperty[Boolean] = BasicDiscreteObservableConfigProperty(BasicDiscreteConfigProperty.createBooleanProperty("restart_animation", "Restart Animation"))
  val animationSpeed: ObservableConfigProperty[Integer] = ObservableConfigProperty(BasicConfigProperty[Integer]("animation_speed", 100, "Playback Speed (in %)"))

  val widthDimension: ObservableConfigProperty[Integer] = ObservableConfigProperty(BasicConfigProperty[Integer]("pane_width", 800, "Width of MainPane"))
  val heightDimension: ObservableConfigProperty[Integer] = ObservableConfigProperty(BasicConfigProperty[Integer]("pane_height", 600, "Height of MainPane"))

  val centerBackgroundImage: ObservableConfigProperty[Boolean] = BasicDiscreteObservableConfigProperty(BasicDiscreteConfigProperty.createBooleanProperty("center_background_image", "Center Background Image"))

  val backgroundImageTransparency: ObservableConfigProperty[Integer] = ObservableConfigProperty(BasicConfigProperty[Integer]("back_img_alpha", 50, "Alpha of Background Image (0-255)"))
}

object Eva1GraphicConfig {

  private def createPaneDimensionProperty: BasicConfigProperty[(Int, Int)] = BasicConfigProperty("pane_dimension", (800, 600), "Dimension of MainPane")

}
