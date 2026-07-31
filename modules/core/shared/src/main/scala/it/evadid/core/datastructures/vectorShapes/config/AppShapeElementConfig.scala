package it.evadid.core.datastructures.vectorShapes.config

import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.Dimension

trait AppShapeElementConfig[T: Fractional] {

  def useCustomPadding: Option[Dimension[T]]

  // def useCustomMargin: Option[Dimension[T]]

  def font: AppFont

  def colorStroke: AppColor

  def colorFill: AppColor

  def colorFont: AppColor

  def onMouseClicked(leftButton: Boolean): Unit


}


object AppShapeElementConfig {

  def EvaShapeConfigDefault[T: Fractional]: AppShapeElementConfig[T] = new AppShapeElementConfig[T]() {
    override def colorStroke: AppColor = RGBColor.red

    override def colorFill: AppColor = RGBColor.red

    override def colorFont: AppColor = RGBColor.yellow

    override def onMouseClicked(leftButton: Boolean): Unit = {
      println(s"AppShapeElementConfig::onMouseClicked(left=${leftButton})")
    }

    override def useCustomPadding: Option[Dimension[T]] = None

    override def font: AppFont = AppFont.AnonymousPro
  }


}

