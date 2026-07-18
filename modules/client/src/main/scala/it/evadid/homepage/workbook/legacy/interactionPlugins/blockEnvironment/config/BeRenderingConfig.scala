package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config

import it.evadid.core.datastructures.color.{AppColorPalette, RGBYColorPalette}
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import todomove.datastructures.web.font.AppFont
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.SegmentType
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeAmendFactory, ShapeAmends}

case class BeRenderingConfig(
                              appFont: AppFont,
                              paddingSmall: Dimension[Double],
                              paddingBig: Dimension[Double],
                              colorPalette: RGBYColorPalette,
                              language: HumanLanguage,
                              private val pControlSegmentSize: Int = 10,
                              controlFlowAmendMap: Map[SegmentType, ShapeAmends] = Map()) {
  val controlSegmentSizeInt: Int = pControlSegmentSize
  val controlSegmentSize: Double = pControlSegmentSize
  val amendFactory: BeShapeAmendFactory = BeShapeAmendFactory(this)
}

object BeRenderingConfig {
  
  def defaultWithLanguage(language: HumanLanguage): BeRenderingConfig =  BeRenderingConfig(AppFont.defaultFont, Dimension[Double](2, 2), Dimension[Double](10, 10), AppColorPalette.defaultRGBYPalette25, language, 10)
  def default(): BeRenderingConfig =  BeRenderingConfig(AppFont.defaultFont, Dimension[Double](2, 2), Dimension[Double](10, 10), AppColorPalette.defaultRGBYPalette25, AppLanguage.English, 10)
    //BeRenderingConfig(AppFont.AnonymousPro, Dimension[Double](5, 5), Dimension[Double](20, 20), AppColorPalette.defaultRGBYPalette25, AppLanguage.English)


}