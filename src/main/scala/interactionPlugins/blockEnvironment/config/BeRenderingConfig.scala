package interactionPlugins.blockEnvironment.config

import contentmanagement.model.AppFont
import contentmanagement.model.color.{AppColorPalette, RGBYColorPalette}
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.{AppLanguage, HumanLanguage}
import contentmanagement.webElements.svg.shapes.BeShapeAmendFactory


case class BeRenderingConfig(appFont: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double], colorPalette: RGBYColorPalette, language: HumanLanguage, controlSegmentSize: Int = 5) {
  val amendFactory: BeShapeAmendFactory = BeShapeAmendFactory(this)
}

object BeRenderingConfig {

  def default(): BeRenderingConfig =  BeRenderingConfig(AppFont.defaultFont, Dimension[Double](2, 2), Dimension[Double](10, 10), AppColorPalette.defaultRGBYPalette25, AppLanguage.English, 5)
    //BeRenderingConfig(AppFont.AnonymousPro, Dimension[Double](5, 5), Dimension[Double](20, 20), AppColorPalette.defaultRGBYPalette25, AppLanguage.English)


}