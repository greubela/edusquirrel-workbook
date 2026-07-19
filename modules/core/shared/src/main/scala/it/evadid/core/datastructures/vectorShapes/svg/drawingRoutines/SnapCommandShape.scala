package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

/** Snap!'s command-block outline, normalized to the requested drawing bounds.
  *
  * Original: `CommandBlockMorph.prototype.outlinePath`, `blocks.js:6886-6952`:
  * `ctx.arc(this.corner, this.corner, radius, radians(-180), radians(-90), false);`
  * followed by Snap's top and bottom jigsaw sequences. The original geometry uses
  * `corner = 3 * scale`, `inset = 6 * scale`, and `dent = 8 * scale` from
  * `SyntaxElementMorph.prototype.setScale`, `blocks.js:264-276`.
  * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
  */
case class SnapCommandShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(3, 0).lineToRel(9, 0).lineToRel(3, 8).lineToRel(8, 0).lineToRel(3, -8).lineToRel(71, 0)
      .lineToRel(3, 8).lineToRel(0, 84).lineToRel(-3, 8).lineToRel(-71, 0)
      .lineToRel(-3, -8).lineToRel(-8, 0).lineToRel(-3, 8).lineToRel(-9, 0).lineToRel(-3, -8).lineToRel(0, -84)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
