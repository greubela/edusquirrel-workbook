package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Modifier, SvgElement, svg}
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.*
import com.raquo.laminar.api.L.seqToModifier

case class AppRectangleSvgElement[T](bounds: Bounds[T], mods: Seq[L.Modifier[L.SvgElement]] = List()) extends AppSvgElement {

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = {
      AppRectangleSvgElement(bounds, newMods ++ mods)
  }
  
  override def staticBoundingBox: Bounds[Double] = bounds.toDouble

  override lazy val renderAsLaminar: L.SvgElement = svg.rect(
    svg.x := bounds.startPoint.x.toString,
    svg.y := bounds.startPoint.y.toString,
    svg.width := bounds.width.toString,
    svg.height := bounds.height.toString
  ).amend(mods)


  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)
  
  override lazy val flatten: List[AppSvgElement] = List(this)
  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)
}
