package interactionPlugins.blockEnvironment.programming.blocks

import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.VBoxSameWidth
import interactionPlugins.blockEnvironment.programming.shapes.controlflow.ControlFlowConnectorBackground




case class NestedBlockRenderer(
                                lines: List[NestedBlockRendererLine],
                                controlFlowShapes: List[List[BeShape]]
                              ) {
  

  def addAllLines(other: NestedBlockRenderer): NestedBlockRenderer = {
    val linesAdded = lines ++ other.lines

    val priorStack = controlFlowShapes.lastOption.getOrElse(List())
    val controlFlowAdded = other.controlFlowShapes.map(curList => priorStack ++ curList)
    NestedBlockRenderer(linesAdded, controlFlowAdded)
  }

  def finishedShape(): BeShape = {
    VBoxSameWidth(lines.map(_.mainShape), false)
  }

}

object NestedBlockRenderer {
  def fromShape(shape: BeShape): NestedBlockRenderer = {
    val line = NestedBlockRendererLine(shape, List(), List(), List())
    NestedBlockRenderer(List(line), List(List(ControlFlowConnectorBackground())))
  }
  

  def empty(): NestedBlockRenderer = NestedBlockRenderer(List(), List())

  def fromLinesWithNewShape(lines: List[NestedBlockRendererLine], newShape: BeShape): NestedBlockRenderer = {
    val navShapes = lines.flatMap(_.navShapes)
    val infoShapes = lines.flatMap(_.infoShapes)
    val sideEffectShapes = lines.flatMap(_.sideEffectShapes)
    val newLine = NestedBlockRendererLine(newShape, navShapes, infoShapes, sideEffectShapes)
    NestedBlockRenderer(List(newLine), List(List(ControlFlowConnectorBackground())))
  }
  
}
