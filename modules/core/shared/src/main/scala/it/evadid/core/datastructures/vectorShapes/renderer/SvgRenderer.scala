package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.util.logging.Logger

trait SvgRenderer[T: Fractional, O] {
  def render(logger: Logger, input: AppShapeElement[T]): O
}

object SvgRenderer {

  case class RenderingResult[T: Fractional, O](input: AppShapeElement[T], output: O)

}
