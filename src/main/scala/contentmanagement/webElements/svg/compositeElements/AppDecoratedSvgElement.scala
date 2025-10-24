package contentmanagement.webElements.svg.compositeElements

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{nodeSeqToModifier, seqToModifier, svg}
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement

case class AppDecoratedSvgElement(mainElement: AppSvgElement, overlays: List[AppSvgElement], underlays: List[AppSvgElement]) extends AppSvgElement {

  override def staticBoundingBox: Bounds[Double] = Bounds.thatContainsAll(mainElement.staticBoundingBox.cornerPoints ++ overlays.flatMap(_.staticBoundingBox.cornerPoints) ++ underlays.flatMap(_.staticBoundingBox.cornerPoints))

  override def mods: Seq[L.Modifier[L.SvgElement]] = mainElement.mods

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = AppDecoratedSvgElement(mainElement.addMods(newMods), overlays, underlays)

  override def renderAsLaminar: L.SvgElement = svg.g(
    underlays.map(_.renderAsLaminar),
    mainElement.renderAsLaminar,
    overlays.map(_.renderAsLaminar),
  )

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement =
    AppDecoratedSvgElement(mainElement.addModsToAll(newMods), overlays.map(_.addModsToAll(newMods)), underlays.map(_.addModsToAll(newMods)))

  override def flatten: List[AppSvgElement] = overlays.flatMap(_.flatten) ++ underlays.flatMap(_.flatten) ++ mainElement.flatten


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = AppDecoratedSvgElement(func(mainElement), overlays.map(func), underlays.map(func))
}
