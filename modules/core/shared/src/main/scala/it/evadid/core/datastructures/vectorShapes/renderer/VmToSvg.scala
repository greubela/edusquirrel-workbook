package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.DuckShape
import it.evadid.util.logging.Logger
import it.evadid.vm.code.abstractions.BeExpression

object VmToSvg {

  def renderBeExpression(logger: Logger, expression: BeExpression): AppShapeElement[Double] = {

    logger.logWarn("VmToSvg::not correctly implemented yet!")

    val minDim = Some(Dimension[Double](10, 10))
    val routine = DuckShape[Double]()
    val shapeConfig = AppShapeElementConfig.EvaShapeConfigDefault[Double]

    val atomar = AppShapeDrawingRoutineElement[Double](routine, shapeConfig, minDim)
    atomar
  }



}
