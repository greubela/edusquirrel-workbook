package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.decorations.{BeDataArrow, ControlArrowLeftRight, PathSplitOverlay, PathUnionOverlay}
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.*
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class RepetitionSplitMiddle() extends ControlFlowShapeDoubleWidth {

  override def minHeightInSegments: Int = 9

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  private def handleParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): (ControlFlowPath, Point[Double], Point[Double]) = {

    val seg = renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    var resPath = path.lastSegment.curPath

    resPath = resPath
      .verticalLineWithHeight(extraHeight / 2.0)
      .verticalLineWithHeight(seg)
      .lineToRel(Dimension[Double](seg / 2.0, seg / 2.0))
      .verticalLineWithHeight(1.5 * seg)

    val unionPoint = resPath.current

    resPath = resPath
      .horizontalLineWithWidth(2.5 * seg)
      .verticalLineWithHeight(2 * seg)

    val resFlowPath = path
      .changeLastPathBuilder(_ => resPath)
      .copy(curStatus = PAUSED)
    val endParentPas = resFlowPath.lastSegment.curPath.current

    (resFlowPath, unionPoint, endParentPas)
  }

  private def startLoopbackPath(unionPoint: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight: Double = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPath = SvgPathBuilder[Double](unionPoint)
      .verticalLineWithHeight(extraHeight / 2.0)
      .verticalLineWithHeight(6.0 * seg)
    val segment = PathSegment(curPath, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment))
  }

  private def startAcceptingPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPath = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](3 * seg, 3 * seg))
      .lineToRel(Dimension[Double](0, seg))
      .verticalLineWithHeight(extraHeight / 2)

    val segment = PathSegment(curPath, List(), false, renderingInfo.renderingConfig.amendFactory.activeTrueConditionControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment))
  }

  private def startStopRepetitionPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPathRed = SvgPathBuilder[Double](startPos)
      /*.lineToRel(Dimension[Double](-2 * seg, 2 * seg))
      .horizontalLineWithWidth(-seg)
      .lineToRel(Dimension[Double](-seg / 2.0, seg / 2.0))
      .verticalLineWithHeight(1.5 * seg)*/
      .lineToRel(Dimension[Double](-3.5 * seg, 3.5 * seg))
      .verticalLineWithHeight(0.5 * seg)

    val curPathInactive = SvgPathBuilder[Double](curPathRed.current)
      .verticalLineWithHeight(extraHeight / 2)

    val segment1 = PathSegment(curPathRed, List(), false, renderingInfo.renderingConfig.amendFactory.activeFalseConditionControlFlowAmends)
    val segment2 = PathSegment(curPathInactive, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveFalseConditionControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment1, segment2))
  }


  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    val (changedParent, unionPoint, parentEndPos): (ControlFlowPath, Point[Double], Point[Double]) = handleParentPath(cf.firstOpenPath, renderingInfo.renderingConfig, curLineHeight)
    cf
      .changeFirstOpenPath(_ => changedParent)
      // start the accepting/repetition path
      .startNewPath(startStopRepetitionPath(parentEndPos, curLineHeight, renderingInfo))
      // loopback path
      .startNewPath(startLoopbackPath(unionPoint, curLineHeight, renderingInfo))
      // active path
      .startNewPath(startAcceptingPath(parentEndPos, curLineHeight, renderingInfo))
      .addDecoration(PathSplitOverlay(), centerPoint.moveWithDimension(Dimension[Double](0, seg/2.0)))
      .addDecoration(BeDataArrow(), centerPoint.moveWithDimension(Dimension[Double](3.0 * seg, 0)))
      .addDecoration(PathUnionOverlay(), unionPoint)
      .addDecoration(ControlArrowLeftRight(false, true), unionPoint)
  }


}