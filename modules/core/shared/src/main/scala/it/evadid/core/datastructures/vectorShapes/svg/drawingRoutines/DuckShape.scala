package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class DuckShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  private def x(value: Double): Double = value / 125 * 100

  private def y(value: Double): Double = value / 50 * 100

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =  {

    builder
      .moveToRel(x(15), y(25))

      .cubicBezierToRel(x(-6), y(0), x(-7), y(0), x(-10), y(-5))
      .cubicBezierToRel(x(-1), y(-1), x(-1), y(-1), x(-2), y(-1))
      .cubicBezierToRel(x(-1), y(0), x(-1), y(0), x(-2), y(1))
      .cubicBezierToRel(x(-3), y(6), x(-1), y(27), x(14), y(30))

      .lineToRel(x(65), y(0))

      // right elements
      .lineToRel(x(20), y(0))

      .cubicBezierToRel(x(11), y(0), x(20), y(-12), x(8), y(-21))
      .cubicBezierToRel(x(-2), y(-2), x(-5), y(-5), x(-3), y(-8))
      .cubicBezierToRel(x(1), y(-1), x(3), y(-1), x(5), y(-1))

      .cubicBezierToRel(x(2), y(0), x(4), y(-1), x(2), y(-3))
      .cubicBezierToRel(x(2), y(-2), x(4), y(-5), x(0), y(-5))

      .cubicBezierToRel(x(-3), y(0), x(-5), y(0), x(-5), y(-3))
      .cubicBezierToRel(x(0), y(-3), x(-5), y(-9), x(-12), y(-9))
      .cubicBezierToRel(x(-10), y(0), x(-12), y(10), x(-10), y(15))
      .cubicBezierToRel(x(2), y(4), x(5), y(10), x(-5), y(10))
  }



  override def hasDesiredAspectRatio: Option[AspectRatio] = Some(AspectRatio(125, 50))
}

object DuckShape {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[DuckShape[T]] =
    upickle.default.readwriter[Unit].bimap(value => (), _ => DuckShape[T]())
}
