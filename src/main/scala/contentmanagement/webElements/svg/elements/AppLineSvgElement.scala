package contentmanagement.webElements.svg.elements

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.{AppSvgElement, AppSvgElementRenderingAdditions}

case class AppLineSvgElement[T: Fractional](startPoint: Point[T], endPoint: Point[T]) extends AppSvgElement {

  private lazy val tUtil = summon[Fractional[T]]
  lazy val startPointDouble: Point[Double] = Point[Double](tUtil.toDouble(startPoint.x), tUtil.toDouble(startPoint.y))
  lazy val endPointDouble: Point[Double] = Point[Double](tUtil.toDouble(endPoint.x), tUtil.toDouble(endPoint.y))
  
  override def staticBoundingBox: Bounds[Double] = Bounds.fromPoints(startPointDouble, endPointDouble)

  override def asLaminar: L.SvgElement = ???

  override def renderAsLaminar(shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???

  override def renderAsGroupWithAdditions(additions: List[AppSvgElementRenderingAdditions], shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???
}
