package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L.svg
import com.raquo.laminar.api.L
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.{AppSvgElement, AppSvgElementRenderingAdditions}
import org.scalajs.dom.MouseEvent
import com.raquo.laminar.api.L.seqToModifier

case class AppLineSvgElement[T: Fractional](startPoint: Point[T], endPoint: Point[T], override val mods: Seq[L.Modifier[L.SvgElement]] = List()) extends AppSvgElement {

  override def staticBoundingBox: Bounds[Double] = Bounds.fromPoints(startPoint.toDouble, endPoint.toDouble)

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = {
    AppLineSvgElement(startPoint, endPoint, newMods ++ mods)
  }
  
  lazy val renderAsLaminar: L.SvgElement =
    svg.line(
      svg.x1 := startPoint.x.toString,
      svg.y1 := startPoint.y.toString,
      svg.x2 := endPoint.x.toString,
      svg.y2 := endPoint.y.toString
    ).amend(mods)


  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)

  override lazy val flatten: List[AppSvgElement] = List(this)


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)
  
  
}
