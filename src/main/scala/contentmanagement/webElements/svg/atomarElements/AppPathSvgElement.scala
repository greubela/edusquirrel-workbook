package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.AppSvgElement
import com.raquo.laminar.api.L.seqToModifier

case class AppPathSvgElement[T: Fractional]
(pathD: String, cornerPoints: List[Point[T]], controlLines: List[AppLineSvgElement[T]],  mods: Seq[L.Modifier[L.SvgElement]] = List())
  extends AppSvgElement {

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = {
      AppPathSvgElement(pathD, cornerPoints, controlLines, newMods ++ mods)
  }

  private val cornerPointsDouble: List[Point[Double]] = cornerPoints.map(_.toDouble)

  val boundingBoxWithControlPoints: Bounds[Double] = Bounds.thatContainsAll(cornerPointsDouble ++ controlLines.flatMap(_.staticBoundingBox.cornerPoints))

  override val staticBoundingBox: Bounds[Double] = {
    val pointList: List[Point[Double]] = cornerPointsDouble
    Bounds.thatContainsAll(pointList)
  }

  override lazy val renderAsLaminar: L.SvgElement = svg.path(svg.d := pathD).amend(mods)

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)


  override lazy val flatten: List[AppSvgElement] = List(this)

  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)
}
