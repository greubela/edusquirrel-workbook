package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.compositions.CompositionHBox
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.MiddleCenter
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.DuckShape
import it.evadid.util.logging.Logger
import it.evadid.vm.code.abstractions.BeExpression

object VmToSvg {

  def renderBeExpression(logger: Logger, expression: BeExpression): AppShapeElement[Double] = {

    logger.logWarn("VmToSvg::not correctly implemented yet!")

    val minDim = Some(Dimension[Double](50, 50))
    val routine = DuckShape[Double]()
    val shapeConfig = AppShapeElementConfig.EvaShapeConfigDefault[Double]

    val textAtomar = AppShapeTextElement[Double]("testtest", shapeConfig)
    val duckAtomar = AppShapeDrawingRoutineElement[Double](routine, shapeConfig, minDim)

    AppShapeComposition(CompositionHBox(MiddleCenter), shapeConfig, List(textAtomar, duckAtomar))
  }



}
