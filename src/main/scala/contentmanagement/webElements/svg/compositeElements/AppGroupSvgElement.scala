package contentmanagement.webElements.svg.compositeElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{nodeSeqToModifier, seqToModifier, svg}
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement

case class AppGroupSvgElement(childElements: List[AppSvgElement], mods: Seq[L.Modifier[L.SvgElement]] = List()) extends AppSvgElement {


  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = {
    AppGroupSvgElement(childElements, newMods ++ mods)
  }

  
  override def staticBoundingBox: Bounds[Double] =
    Bounds.thatContainsAll(childElements.flatMap(_.staticBoundingBox.cornerPoints))

  override def renderAsLaminar: L.SvgElement = {
    svg.g(
      childElements.map(_.renderAsLaminar)
    ).amend(mods)
  }

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement =
    AppGroupSvgElement(childElements.map(_.addModsToAll(newMods)))


  override def flatten: List[AppSvgElement] = childElements.flatMap(_.flatten)


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = AppGroupSvgElement(childElements.map(func))
}
