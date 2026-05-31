package todomove.webElementsOld.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import BeShape.BeShapeAtomic
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.atomarElements.AppTextSvgElement

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
