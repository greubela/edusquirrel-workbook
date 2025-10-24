package contentmanagement.webElements.svg

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Bounds
import org.scalajs.dom.MouseEvent


trait AppSvgElement {

  def staticBoundingBox: Bounds[Double]

  def mods: Seq[L.Modifier[L.SvgElement]]

  def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement

  def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement 

  def map(func: AppSvgElement => AppSvgElement): AppSvgElement
  
  
  def renderAsLaminar: L.SvgElement

  def makeClickable(onClick: MouseEvent => Any): AppSvgElement =
    addMods(List(
      L.onClick --> { event => onClick(event) },
      onContextMenu.preventDefault --> { event => {} }
    ))

  def makeDroppable(onElementDropped: MouseEvent => Any): AppSvgElement =
    addMods(List(
      L.onDragOver.preventDefault --> (_ => ()), // allow dropping on this element
      L.onDrop.preventDefault --> (e => onElementDropped(e))
    ))

  def makeMouseAware(onEnter: MouseEvent => Any, onLeave: MouseEvent => Any): AppSvgElement =
    addMods(List(
      L.onPointerEnter --> (e => onEnter(e)),
      L.onPointerLeave --> (e => {
        println("!?!?!?!?!? -> " + onLeave)
        onLeave(e)
      })
    ))

  def flatten: List[AppSvgElement]
}
