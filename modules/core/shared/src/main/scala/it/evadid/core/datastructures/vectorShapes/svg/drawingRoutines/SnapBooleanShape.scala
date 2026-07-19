package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

/** Snap!'s predicate (diamond) reporter outline, normalized to the requested bounds.
  *
  * Original: `ReporterBlockMorph.prototype.outlinePathDiamond`, `blocks.js:7827-7851`:
  * `ctx.moveTo(inset, h2); ctx.lineTo(r, inset);`
  * `ctx.lineTo(right - inset, inset); ctx.lineTo(w - inset, h2);`
  * `ctx.lineTo(right - inset, h - inset); ctx.lineTo(r, h - inset);`
  * Its `rounding = 9 * scale` is defined by
  * `SyntaxElementMorph.prototype.setScale`, `blocks.js:264-276`.
  * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
  */
case class SnapBooleanShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(0, 50).lineToRel(15, -50).lineToRel(70, 0).lineToRel(15, 50).lineToRel(-15, 50).lineToRel(-70, 0)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
