package contentmanagement.webElements.svg.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.{AppSvgElement, AppSvgElementRenderingAdditions}

case class AppPathSvgElement[T: Fractional]
(pathD: String, cornerPoints: List[Point[T]], controlLines: List[AppLineSvgElement[T]])
  extends AppSvgElement {

  private val tUtil = summon[Fractional[T]]

  private val cornerPointsDouble: List[Point[Double]] =
    cornerPoints.map(p => Point[Double](tUtil.toDouble(p.x), tUtil.toDouble(p.y)))

  val d: Float = 3.0

  override val staticBoundingBox: Bounds[Double] = {
    val pointList: List[Point[Double]] = cornerPointsDouble ++ controlLines.flatMap(_.staticBoundingBox.cornerPoints)
    Bounds.thatContainsAll(pointList)
  }

  override def asLaminar: L.SvgElement = svg.path(
    svg.d := pathD
  )

  override def renderAsLaminar(shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???

  override def renderAsGroupWithAdditions(additions: List[AppSvgElementRenderingAdditions], shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???
}
