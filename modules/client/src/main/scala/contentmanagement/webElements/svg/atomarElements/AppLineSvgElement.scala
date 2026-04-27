package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, svg}
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.{Bounds, Point}

case class AppLineSvgElement[T: Fractional](
                                             startPoint: Point[T],
                                             endPoint: Point[T],
                                             override val mods: Seq[L.Modifier[L.SvgElement]] = List(),
                                             override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()
                                           ) extends AppSvgElement {

  override def staticBoundingBox: Bounds[Double] = Bounds.fromPoints(startPoint.toDouble, endPoint.toDouble)

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)

  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)

  def renderBeforeMods: L.SvgElement =
    svg.line(
      svg.x1 := startPoint.x.toString,
      svg.y1 := startPoint.y.toString,
      svg.x2 := endPoint.x.toString,
      svg.y2 := endPoint.y.toString
    )


  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)

  override lazy val flatten: List[AppSvgElement] = List(this)


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)

  def removeAllMods(): AppSvgElement = {
    AppLineSvgElement(startPoint, endPoint, List())
  }

}
