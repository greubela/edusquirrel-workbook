package contentmanagement.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import BeShape.BeShapeAtomic
import datastructures.core.language.{HumanLanguage, LanguageMap}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}

case class TextShape(languageMap: LanguageMap[HumanLanguage], amends: Seq[L.Modifier[L.SvgElement]] = List()) extends BeShapeAtomic {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] =
    config.appFont.measureText(languageMap.getInLanguage(config.language)).increaseSize(config.paddingSmall)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val useAmends = if (amends.isEmpty) {
      List(
        svg.fill := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
        svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString
      )
    }else{
      amends
    }

    AppTextSvgElement(languageMap.getInLanguage(rendererConfig.language), bounds, rendererConfig.appFont).addMods(useAmends)
  }

}
