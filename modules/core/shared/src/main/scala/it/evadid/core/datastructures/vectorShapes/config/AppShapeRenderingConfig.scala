package it.evadid.core.datastructures.vectorShapes.config

import it.evadid.core.datastructures.color.RGBYColorPalette
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.util.logging.Logger

case class AppShapeRenderingConfig[
  T: Fractional
](
   renderingLogger: Logger,

   appFont: String, // todo: move App Font to core
   renderingLanguage: HumanLanguage,

   colorPalette: RGBYColorPalette,

   defaultPadding: Dimension[T],
   defaultGapBetweenElements: Dimension[T],
  // defaultMargin: Dimension[T],

   gapBetweenConsecutiveShapes: Dimension[T],
 )