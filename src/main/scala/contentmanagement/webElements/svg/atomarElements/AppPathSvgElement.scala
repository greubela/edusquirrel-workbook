package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.AppSvgElement

case class AppPathSvgElement[T: Fractional]
(
  pathD: String,
  cornerPoints: List[Point[T]],
  controlLines: List[AppLineSvgElement[T]],
  override val mods: Seq[L.Modifier[L.SvgElement]] = List(),
  override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()
) extends AppSvgElement {



  private val cornerPointsDouble: List[Point[Double]] = cornerPoints.map(_.toDouble)

  val boundingBoxWithControlPoints: Bounds[Double] = Bounds.thatContainsAll(cornerPointsDouble ++ controlLines.flatMap(_.staticBoundingBox.cornerPoints))

  override val staticBoundingBox: Bounds[Double] = {
    val pointList: List[Point[Double]] = cornerPointsDouble
    Bounds.thatContainsAll(pointList)
  }

  def renderBeforeMods: L.SvgElement  = svg.path(svg.d := pathD)

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)
  
  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)
  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)
  
  override lazy val flatten: List[AppSvgElement] = List(this)

  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)


  def removeAllMods(): AppSvgElement = {
    AppPathSvgElement(pathD, cornerPoints, controlLines, List())
  }

}
