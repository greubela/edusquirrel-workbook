package contentmanagement.webElements.svg.builder.controlFlow

import contentmanagement.model.geometry.Point
import contentmanagement.webElements.svg.builder.controlFlow.path.ControlFlowPathOverlay
import contentmanagement.webElements.svg.builder.controlFlow.types.doubleWidth.{ControlFlowCrossType, IfElseSplitType, IfElseUnionType}
import contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth.{ControlFlowDirectedType, ControlFlowProgramStarterType, ControlFlowProgramStopperType}
import contentmanagement.webElements.svg.shapes.ControlFlowShape
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.{ControlFlowCross, IfElseSplit, IfElseUnion}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowDirected, ControlFlowProgramStarter, ControlFlowProgramStopper}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import munit.FunSuite

class ControlFlowPathParitySpec extends FunSuite {

  private def legacyPaths(shapes: List[ControlFlowShape], renderingConfig: BeRenderingConfig): List[String] = {
    val renderingInfo = RenderingInformation(null, null, renderingConfig, null, null, _ => ())
    val centerPoint = Point[Double](0, 0)
    var overlay = ControlFlowOverlayBuilder(List(), List(), Point(0, 0))
    shapes.foreach { shape =>
      val lineHeight = renderingConfig.controlSegmentSize * shape.minHeightInSegments
      overlay = shape.renderControlFlow(overlay, renderingInfo, centerPoint, lineHeight)
      overlay = overlay.resetHandledToOpen()
    }
    overlay.paths.flatMap(_.segments.map(_.curPath.toSvgPathD)).sorted
  }

  private def newPaths(types: List[ControlFlowType], renderingConfig: BeRenderingConfig): List[String] = {
    var overlay = ControlFlowPathOverlay(List(), List())
    types.foreach { controlFlowType =>
      overlay = controlFlowType.renderPaths(renderingConfig, overlay)
      overlay = overlay.resetHandledToOpen()
    }
    overlay.pathStack.flatMap(_.segments.map(_.curPath.toSvgPathD)).sorted
  }

  test("new control-flow types produce the same paths as legacy control-flow shapes") {
    val renderingConfig = BeRenderingConfig.default()
    val legacySequence = List[ControlFlowShape](
      ControlFlowProgramStarter(),
      ControlFlowDirected(goesDown = true),
      IfElseSplit(),
      ControlFlowCross(),
      IfElseUnion(),
      ControlFlowDirected(goesDown = true),
      ControlFlowProgramStopper()
    )

    val newSequence = List[ControlFlowType](
      ControlFlowProgramStarterType(),
      ControlFlowDirectedType(goesDown = true),
      IfElseSplitType(),
      ControlFlowCrossType(),
      IfElseUnionType(),
      ControlFlowDirectedType(goesDown = true),
      ControlFlowProgramStopperType()
    )

    assertEquals(newPaths(newSequence, renderingConfig), legacyPaths(legacySequence, renderingConfig))
  }
}
