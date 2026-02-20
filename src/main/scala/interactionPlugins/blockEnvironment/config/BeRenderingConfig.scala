package interactionPlugins.blockEnvironment.config

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal
import contentmanagement.model.AppFont
import contentmanagement.model.color.{AppColorPalette, RGBYColorPalette}
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.{AppLanguage, HumanLanguage}
import contentmanagement.webElements.svg.builder.controlFlow.path.SegmentType
import contentmanagement.webElements.svg.shapes.{BeShapeAmendFactory, ShapeAmends}


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