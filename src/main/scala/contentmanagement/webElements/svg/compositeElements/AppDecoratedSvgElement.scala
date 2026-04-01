package contentmanagement.webElements.svg.compositeElements

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.AppSvgElement
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, nodeSeqToModifier, seqToModifier, svg}
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.Bounds

case class AppDecoratedSvgElement(mainElement: AppSvgElement, overlays: List[AppSvgElement], underlays: List[AppSvgElement]) extends AppSvgElement {

  override def staticBoundingBox: Bounds[Double] = 
    Bounds.thatContainsAll(mainElement.staticBoundingBox.cornerPoints ++ overlays.flatMap(_.staticBoundingBox.cornerPoints) ++ underlays.flatMap(_.staticBoundingBox.cornerPoints))

  override def mods: Seq[L.Modifier[L.SvgElement]] = mainElement.mods
  override def signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = mainElement.signalMods
  
  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mainElement = mainElement.addMods(newMods))
  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(mainElement = mainElement.addSignalMods(newMods))
  
  
  override def renderBeforeMods: L.SvgElement = svg.g(
    underlays.map(_.renderWithMods),
    mainElement.renderWithMods,
    overlays.map(_.renderWithMods),
  )

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement =
    AppDecoratedSvgElement(mainElement.addModsToAll(newMods), overlays.map(_.addModsToAll(newMods)), underlays.map(_.addModsToAll(newMods)))

  override def flatten: List[AppSvgElement] = overlays.flatMap(_.flatten) ++ underlays.flatMap(_.flatten) ++ mainElement.flatten


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = AppDecoratedSvgElement(func(mainElement), overlays.map(func), underlays.map(func))


  def removeAllMods(): AppSvgElement = {
    AppDecoratedSvgElement(mainElement.removeAllMods(), overlays.map(_.removeAllMods()), underlays.map(_.removeAllMods()))
  }

}
