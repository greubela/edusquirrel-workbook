package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Modifier, Signal, SvgElement, seqToModifier, svg}
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.*

case class AppRectangleSvgElement[T](
                                      bounds: Bounds[T],
                                      override val  mods: Seq[L.Modifier[L.SvgElement]] = List(),
                                      override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()
                                    ) extends AppSvgElement {

  
  override def staticBoundingBox: Bounds[Double] = bounds.toDouble

  def renderBeforeMods: L.SvgElement = svg.rect(
    svg.x := bounds.startPoint.x.toString,
    svg.y := bounds.startPoint.y.toString,
    svg.width := bounds.width.toString,
    svg.height := bounds.height.toString
  )


  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)

  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)
  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)
  
  override lazy val flatten: List[AppSvgElement] = List(this)
  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)


  def removeAllMods(): AppSvgElement = {
    AppRectangleSvgElement(bounds, List())
  }

}
