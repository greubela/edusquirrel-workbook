package todomove.webElementsOld.webElements.svg.shapes

import it.evadid.core.datastructures.vectorShapes.blocks.BlockPaths

import it.evadid.core.datastructures.geometry.Bounds
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder

/**
  * Test-only accessors for the block and Snap path builders.
  */
object TestShapeFactoryAccess {

  def duck(bounds: Bounds[Double]): SvgPathBuilder[Double] = BlockPaths.buildDuckShape(bounds)

  def literal(bounds: Bounds[Double]): SvgPathBuilder[Double] = BlockPaths.buildLiteralShape(bounds)

  def snapCommand(bounds: Bounds[Double], cSlots: List[Bounds[Double]] = Nil): SvgPathBuilder[Double] =
    SnapShapeFactory.buildSnapCommandShape(
      bounds,
      cSlots = cSlots.map(SnapShapeFactory.SnapCSlot.apply)
    )

  def snapHat(bounds: Bounds[Double], cSlots: List[Bounds[Double]] = Nil): SvgPathBuilder[Double] =
    SnapShapeFactory.buildSnapHatShape(
      bounds,
      cSlots = cSlots.map(SnapShapeFactory.SnapCSlot.apply)
    )

  def snapCShape(bounds: Bounds[Double], cSlots: List[Bounds[Double]]): SvgPathBuilder[Double] =
    SnapShapeFactory.buildSnapCShape(bounds, cSlots)

  def snapReporter(bounds: Bounds[Double], predicate: Boolean): SvgPathBuilder[Double] =
    SnapShapeFactory.buildSnapReporterShape(bounds, isPredicate = predicate)
}
