package interactionPlugins.blockEnvironment.programming.shapes.atomic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.BeShapeAtomic

case class TextShape(languageMap: LanguageMap[HumanLanguage]) extends BeShapeAtomic {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] =
    config.appFont.measureText(languageMap.getInLanguage(config.language)).increaseSize(config.paddingSmall)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement =
    AppTextSvgElement(languageMap.getInLanguage(rendererConfig.language), bounds, rendererConfig.appFont).addMods(List(
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString
    ))

}
