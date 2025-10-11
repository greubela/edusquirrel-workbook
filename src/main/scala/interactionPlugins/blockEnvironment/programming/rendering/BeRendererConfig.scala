package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.AppFont
import contentmanagement.model.color.RGBYColorPalette
import contentmanagement.model.geometry.Dimension


case class BeRendererConfig(appFont: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double], colorPalette: RGBYColorPalette)