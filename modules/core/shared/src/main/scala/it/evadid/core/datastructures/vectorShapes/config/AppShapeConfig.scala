package it.evadid.core.datastructures.vectorShapes.config

import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.geometry.Dimension

trait AppShapeConfig[T : Fractional] {

  def renderingConfig: AppShapeRenderingConfig[T]

  def useCustomPadding: Option[Dimension[T]]

 // def useCustomMargin: Option[Dimension[T]]


  def colorStroke: AppColor

  def colorFill: AppColor

  def colorFont: AppColor

  def onMouseClicked(leftButton: Boolean): Unit


}


object AppShapeConfig {


  def EvaShapeConfigDefault[T: Fractional](pRenderingConfig: AppShapeRenderingConfig[T]): AppShapeConfig[T] = new AppShapeConfig[T]() {

    override def colorStroke: AppColor = RGBColor.red

    override def colorFill: AppColor = RGBColor.red

    override def colorFont: AppColor = RGBColor.yellow

    override def onMouseClicked(leftButton: Boolean): Unit = {}

    override def renderingConfig: AppShapeRenderingConfig[T] = pRenderingConfig

    override def useCustomPadding: Option[Dimension[T]] = None
  }


}

