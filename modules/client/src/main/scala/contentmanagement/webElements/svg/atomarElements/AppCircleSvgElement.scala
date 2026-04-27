package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, svg}
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.{Bounds, Dimension, Point}

case class AppCircleSvgElement[T: Fractional](
    center: Point[T],
    radius: T,
    override val mods: Seq[L.Modifier[L.SvgElement]] = List(),
    override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()
) extends AppSvgElement {

  private val numeric = summon[Fractional[T]]
  import numeric.*

  override val staticBoundingBox: Bounds[Double] = {
    val diameter = radius * fromInt(2)
    Bounds[T](
      Point(center.x - radius, center.y - radius),
      Dimension(diameter, diameter)
    ).toDouble
  }

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)

  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement =
    this.copy(signalMods = newMods ++ signalMods)

  override def renderBeforeMods: L.SvgElement =
    svg.circle(
      svg.cx := center.x.toString,
      svg.cy := center.y.toString,
      svg.r := radius.toString
    )

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)

  override lazy val flatten: List[AppSvgElement] = List(this)

  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)

  override def removeAllMods(): AppSvgElement = AppCircleSvgElement(center, radius, List())
}
