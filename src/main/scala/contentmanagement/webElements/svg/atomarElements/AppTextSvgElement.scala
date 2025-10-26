package contentmanagement.webElements.svg.atomarElements

import com.raquo.laminar.api.L.{Signal, seqToModifier, svg, textToTextNode}
import com.raquo.laminar.api.{L, textToTextNode}
import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement

case class AppTextSvgElement[T: Fractional](displayText: String, pBounds: Bounds[T], font: AppFont,
                                            override val mods: Seq[L.Modifier[L.SvgElement]] = List(),
                                            override val signalMods: Seq[Signal[L.Modifier[L.SvgElement]]] = List()) extends AppSvgElement {


  override def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement = this.copy(mods = newMods ++ mods)
  override def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement = this.copy(signalMods = newMods ++ signalMods)

  override val staticBoundingBox: Bounds[Double] = pBounds.toDouble

  def renderBeforeMods: L.SvgElement = {
    val textDim = font.measureText(displayText).toDouble
    val freeSpaceX = staticBoundingBox.width - textDim.width
    val freeSpaceY = staticBoundingBox.height - textDim.height
    svg.text(
      svg.x := (staticBoundingBox.startX + freeSpaceX / 2).toString,
      svg.y := (staticBoundingBox.endY - freeSpaceY / 2).toString,
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
