package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.doubleWidth

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPath, ControlFlowPathOverlay, ControlFlowPathSegment, PathStatus, PathType, SegmentType}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

case class ControlFlowCrossType() extends ControlFlowTypeDoubleWidth {

  override def minHeightInSegments: Int = 5

  override def backgroundShape: BeShapeContainerable =
    ControlFlowConnectorBackground(List((true, true), (true, true)))

  private def extendAndAppendParentPath(path: ControlFlowPath, curLineHeight: Double, seg: Double, toTheRight: Boolean, newSegmentType: SegmentType): ControlFlowPath = {
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    def xDir(dist: Double): Double = if (toTheRight) dist else -dist

    def addMiddleSection(path: SvgPathBuilder[Double]): SvgPathBuilder[Double] = {
      path
        .horizontalLineWithWidth(xDir(2 * seg))
        .lineToRel(Dimension[Double](xDir(seg), seg))
        .horizontalLineWithWidth(xDir(2 * seg))
    }

    var continuedPath = path.segments.last.curPath
      .verticalLineWithHeight(extraHeight / 2)
      .verticalLineWithHeight(1.0 * seg)
      .lineToRel(Dimension[Double](xDir(seg / 2.0), seg / 2.0))

    if (toTheRight) {
      continuedPath = addMiddleSection(continuedPath)
    }

    val endPos = continuedPath.current

    var secondPath = SvgPathBuilder[Double](endPos)
    if (!toTheRight) {
      secondPath = addMiddleSection(secondPath)
    }
    secondPath = secondPath
      .lineToRel(Dimension[Double](xDir(seg / 2.0), seg / 2.0))
      .verticalLineWithHeight(2 * seg)
      .verticalLineWithHeight(extraHeight / 2)

    path.copy(
      curStatus = PathStatus.HANDLED,
      segments = path.segments.init :+ path.segments.last.copy(curPath = continuedPath) :+ ControlFlowPathSegment(secondPath, newSegmentType)
    )
  }

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments

    val updatedFalse = oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.CONDITION_FALSE).map {
      case (path, index) =>
        val updatedPath = extendAndAppendParentPath(path, curLineHeight, seg, toTheRight = true, SegmentType.FALSE_SEGMENT_ACTIVE)
        (updatedPath, index)
    }

    val updatedTrue = oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.CONDITION_TRUE).map {
      case (path, index) =>
        val updatedPath = extendAndAppendParentPath(path, curLineHeight, seg, toTheRight = false, SegmentType.TRUE_SEGMENT_INACTIVE)
        (updatedPath, index)
    }

    (updatedFalse, updatedTrue) match {
      case (Some((falsePath, falseIndex)), Some((truePath, trueIndex))) =>
        oldOverlay
          .replacePath(falseIndex, falsePath, setToHandled = false)
          .replacePath(trueIndex, truePath, setToHandled = false)
      case (Some((falsePath, falseIndex)), None) =>
        oldOverlay.replacePath(falseIndex, falsePath, setToHandled = false)
      case (None, Some((truePath, trueIndex))) =>
        oldOverlay.replacePath(trueIndex, truePath, setToHandled = false)
      case _ => oldOverlay
    }
  }
}
