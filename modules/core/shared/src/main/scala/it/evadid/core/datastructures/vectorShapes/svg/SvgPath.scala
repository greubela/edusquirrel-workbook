package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Dimension

sealed trait SvgPath {
  def svgPathDString: String
}

object SvgPath {

  def fromRenderingRoutine[T](dimension: Dimension[T], factory: Dimension[T] => SvgPathBuilder[T]): SvgPath = BuilderBasedSvgPath[T](dimension, factory(dimension))

  case class BuilderBasedSvgPath[T](desiredDimension: Dimension[T], pathBuilder: SvgPathBuilder[T]) extends SvgPath {
    override def svgPathDString: String = pathBuilder.toSvgPathD
  }

}

