package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

/** Snap!'s hat-block outline, normalized to the requested drawing bounds.
  *
  * Original: `HatBlockMorph.prototype.outlinePath`, `blocks.js:7297-7372`:
  * `r = ((4 * h * h) + (s * s)) / (8 * h);`
  * `a = degrees(4 * Math.atan(2 * h / s));`
  * `ctx.bezierCurveTo(s, 0, s, h, sp, h);`
  * Geometry constants originate in `SyntaxElementMorph.prototype.setScale`,
  * `blocks.js:264-276`. Vendored at
  * `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
  */
case class SnapHatShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(0, 20).cubicBezierToRel(12, -20, 28, -20, 40, 0).lineToRel(57, 0)
      .lineToRel(3, 8).lineToRel(0, 72).lineToRel(-3, 8).lineToRel(-71, 0)
      .lineToRel(-3, -8).lineToRel(-8, 0).lineToRel(-3, 8).lineToRel(-9, 0).lineToRel(-3, -8).lineToRel(0, -72)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
