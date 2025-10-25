package interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.BeShapeAtomic

case class TextShape(languageMap: LanguageMap[HumanLanguage]) extends BeShapeAtomic {

  override def displaySize(config: BeRendererConfig): Dimension[Double] = config.appFont.measureText(languageMap.getInLanguage(config.language))

  override def renderColorless(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = renderDefaultColoring(config, bounds)

  override def renderDefaultColoring(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement =
    AppTextSvgElement(languageMap.getInLanguage(config.language), bounds, config.appFont).addMods(List(
      svg.fill := config.colorPalette.grayscale(1).toWebStyleString,
     // svg.stroke := config.colorPalette.grayscale(4).toWebStyleString,
    ))

}
