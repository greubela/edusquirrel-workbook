package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

/** Snap!'s oval reporter outline, normalized to the requested drawing bounds.
  *
  * Original: `ReporterBlockMorph.prototype.outlinePathOval`, `blocks.js:7771-7825`:
  * `ctx.arc(r, r, radius, radians(-180), radians(-90), false);`
  * followed by the matching top-right, bottom-right, and bottom-left arcs.
  * Snap uses `rounding = 9 * scale` from
  * `SyntaxElementMorph.prototype.setScale`, `blocks.js:264-276`.
  * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
  */
case class SnapReporterShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(0, 50).quadraticBezierWithRel(0, -50, 15, -50).lineToRel(70, 0)
      .quadraticBezierWithRel(15, 0, 15, 50).quadraticBezierWithRel(0, 50, -15, 50)
      .lineToRel(-70, 0).quadraticBezierWithRel(-15, 0, -15, -50)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
