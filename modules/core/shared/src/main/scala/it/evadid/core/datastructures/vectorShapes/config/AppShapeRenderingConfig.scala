package it.evadid.core.datastructures.vectorShapes.config

import it.evadid.core.datastructures.color.{AppColorPalette, RGBYColorPalette}
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.MiddleCenter
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger

case class AppShapeRenderingConfig[
  T: Fractional
](
   renderingLogger: Logger = Logger.withNameAndPrefixes(Some("RenderingLogger"), PrintToStdLogger.printEverything),
   alignMisfits: AlignmentInParent = MiddleCenter,

   appFont: String = "Calibri", // todo: move App Font to core
   renderingLanguage: HumanLanguage = English,

   colorPalette: RGBYColorPalette = AppColorPalette.defaultRGBYPalette25,

   defaultPadding: Dimension[T],
   defaultGapBetweenElements: Dimension[T],
   // defaultMargin: Dimension[T],

   gapBetweenConsecutiveShapes: Dimension[T],
 )

object AppShapeRenderingConfig {

  val defaultDouble: AppShapeRenderingConfig[Double] = new AppShapeRenderingConfig[Double](
    defaultPadding = Dimension[Double](2, 3),
    defaultGapBetweenElements = Dimension[Double](10, 15),
    gapBetweenConsecutiveShapes = Dimension[Double](20, 25),
  )


}
