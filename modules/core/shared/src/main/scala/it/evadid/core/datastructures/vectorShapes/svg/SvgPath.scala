package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, RelativeBounds}

sealed trait SvgPath {
  def svgPathDString: String
}

object SvgPath {
  private case class StoredPath(svgPathDString: String) extends SvgPath derives upickle.default.ReadWriter

  given upickle.default.ReadWriter[SvgPath] = upickle.default.readwriter[String].bimap(
    _.svgPathDString, StoredPath.apply)

  //def fromRenderingRoutine[T](dimension: Dimension[T], factory: Dimension[T] => SvgPathBuilder[T]): SvgPath = BuilderBasedSvgPath[T](dimension, factory(dimension))

  case class BuilderBasedSvgPath[T](bounds: Bounds[T], pathBuilder: SvgPathBuilder[T]) extends SvgPath {
    override def svgPathDString: String = pathBuilder.toSvgPathD
  }

  object BuilderBasedSvgPath {
    given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[BuilderBasedSvgPath[T]] =
      upickle.default.readwriter[(Bounds[T], SvgPathBuilder[T])].bimap(
        path => (path.bounds, path.pathBuilder), value => BuilderBasedSvgPath(value._1, value._2))
  }

}

