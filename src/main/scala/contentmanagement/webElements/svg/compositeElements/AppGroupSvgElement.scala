package contentmanagement.webElements.svg.compositeElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, nodeSeqToModifier, seqToModifier, svg}
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.Bounds

case class AppGroupSvgElement(childElements: List[AppSvgElement], mods: Seq[L.Modifier[L.SvgElement]] = List(), override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()) extends AppSvgElement {

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)

  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)

  override def staticBoundingBox: Bounds[Double] = {
    Bounds.thatContainsAll(childElements.flatMap(_.staticBoundingBox.cornerPoints))
  }

  override def renderBeforeMods: L.SvgElement = {
    svg.g(
      childElements.map(_.renderWithMods)
    )
  }

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement =
    AppGroupSvgElement(childElements.map(_.addModsToAll(newMods)))


  override def flatten: List[AppSvgElement] = childElements.flatMap(_.flatten)


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = AppGroupSvgElement(childElements.map(func))


  def removeAllMods(): AppSvgElement = {
    AppGroupSvgElement(childElements.map(_.removeAllMods()))
  }

}
