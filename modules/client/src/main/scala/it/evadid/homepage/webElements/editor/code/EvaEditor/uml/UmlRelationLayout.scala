package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import it.evadid.core.datastructures.uml.UmlRelation

/** Routes orthogonal relationships between the measured class boxes. */
object UmlRelationLayout {
  final case class Box(left: Double, top: Double, width: Double, height: Double) {
    def right: Double = left + width
    def bottom: Double = top + height
    def x: Double = left + width / 2
    def y: Double = top + height / 2
  }
  final case class Arrow(relation: UmlRelation, path: String, labelX: Double, labelY: Double)

  def route(relation: UmlRelation, from: Box, to: Box): Option[Arrow] = {
    def vertical(start: Double, end: Double) = Arrow(relation,
      s"M ${from.x} $start V ${(start + end) / 2} H ${to.x} V $end", to.x + 6, end - 6)
    def horizontal(start: Double, end: Double) = Arrow(relation,
      s"M $start ${from.y} H ${(start + end) / 2} V ${to.y} H $end", (start + end) / 2 + 6, to.y - 6)
    if from.width <= 0 || to.width <= 0 || relation.from == relation.to then None
    else if to.bottom <= from.top then Some(vertical(from.top, to.bottom))
    else if from.bottom <= to.top then Some(vertical(from.bottom, to.top))
    else if from.right <= to.left then Some(horizontal(from.right, to.left))
    else if to.right <= from.left then Some(horizontal(from.left, to.right))
    else None
  }
}
