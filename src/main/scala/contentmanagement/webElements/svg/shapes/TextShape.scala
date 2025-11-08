package contentmanagement.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import BeShape.BeShapeAtomic

case class TextShape(languageMap: LanguageMap[HumanLanguage], amends: Seq[L.Modifier[L.SvgElement]] = List()) extends BeShapeAtomic {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] =
    config.appFont.measureText(languageMap.getInLanguage(config.language)).increaseSize(config.paddingSmall)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val useAmends = if (amends.isEmpty) {
      List(
        svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
        svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString
      )
    }else{
      amends
    }

    AppTextSvgElement(languageMap.getInLanguage(rendererConfig.language), bounds, rendererConfig.appFont).addMods(useAmends)
  }

}
