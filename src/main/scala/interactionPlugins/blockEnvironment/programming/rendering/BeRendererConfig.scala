package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.AppFont
import contentmanagement.model.color.{AppColorPalette, RGBYColorPalette}
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.{AppLanguage, HumanLanguage}


case class BeRendererConfig(appFont: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double], colorPalette: RGBYColorPalette, language: HumanLanguage)

object BeRendererConfig {

  def default(): BeRendererConfig = BeRendererConfig(AppFont.AnonymousPro, Dimension[Double](7, 7), Dimension[Double](37, 37), AppColorPalette.defaultRGBYPalette25, AppLanguage.English)


}