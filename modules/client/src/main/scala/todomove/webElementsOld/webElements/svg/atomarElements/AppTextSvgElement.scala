package todomove.webElementsOld.webElements.svg.atomarElements

import com.raquo.laminar.api.L.{Signal, seqToModifier, svg, textToTextNode}
import com.raquo.laminar.api.{L, textToTextNode}
import it.evadid.core.datastructures.geometry.Bounds
import it.evadid.core.datastructures.font.AppFont
import todomove.webElementsOld.webElements.svg.AppSvgElement

case class AppTextSvgElement[T: Fractional](displayText: String, pBounds: Bounds[T], font: AppFont,
                                            override val mods: Seq[L.Modifier[L.SvgElement]] = List(),
                                            override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()) extends AppSvgElement {


  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)
  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)

  override val staticBoundingBox: Bounds[Double] = pBounds.toDouble

  def renderBeforeMods: L.SvgElement = {
    val centerX = staticBoundingBox.startX + staticBoundingBox.width / 2
    val centerY = staticBoundingBox.startY + staticBoundingBox.height / 2
    svg.text(
      svg.x := centerX.toString,
      svg.y := centerY.toString,
      svg.textAnchor := "middle",
      svg.dominantBaseline := "middle",
      svg.fontSize := font.sizeInPx.toString,
      svg.fontFamily := font.name,
      svg.tspan(displayText)
    )
  }

  override def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = addMods(newMods)

  override lazy val flatten: List[AppSvgElement] = List(this)


  override def map(func: AppSvgElement => AppSvgElement): AppSvgElement = func(this)


  def removeAllMods(): AppSvgElement = {
    AppTextSvgElement(displayText, pBounds, font, List())
  }
}
