package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.AspectRatio
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

/** Snap!'s command outline with a C-slot, normalized to the requested bounds.
 *
 * The enclosing outline is `CommandBlockMorph.prototype.outlinePath`,
 * `blocks.js:6886-6952`. The indentation is from
 * `CSlotMorph.prototype.outlinePath`, `blocks.js:10673-10750`:
 * `ctx.lineTo(this.width() + ox - inset, oy);` followed by Snap's five
 * `lineTo` calls forming the inner jigsaw dent and the two inner corner arcs.
 * Its corner/inset/dent constants are defined by
 * `SyntaxElementMorph.prototype.setScale`, `blocks.js:264-276`.
 * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
 */
case class SnapCShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(3, 0).lineToRel(9, 0).lineToRel(3, 8).lineToRel(8, 0).lineToRel(3, -8).lineToRel(71, 0)
      .lineToRel(3, 8).lineToRel(0, 22).lineToRel(-70, 0).lineToRel(-3, 8).lineToRel(-8, 0)
      .lineToRel(-3, -8).lineToRel(-6, 0).lineToRel(0, 40).lineToRel(87, 0).lineToRel(3, 8)
      .lineToRel(0, 14).lineToRel(-3, 8).lineToRel(-71, 0).lineToRel(-3, -8).lineToRel(-8, 0)
      .lineToRel(-3, 8).lineToRel(-9, 0).lineToRel(-3, -8).lineToRel(0, -84)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
