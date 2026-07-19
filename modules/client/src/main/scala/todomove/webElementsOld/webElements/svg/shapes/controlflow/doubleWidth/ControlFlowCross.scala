package todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth

import com.raquo.laminar.api.L
import ControlFlowCross.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.*
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.compositeElements.AppDecoratedSvgElement
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import todomove.webElementsOld.webElements.svg.shapes.decorations.{ControlArrowCross, PathCrossOverlay}

case class ControlFlowCross() extends ControlFlowShapeDoubleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, true)))

  override def minHeightInSegments: Int = 5

  private def extendAndAppendParentPath(path: ControlFlowPath, curLineHeight: Double, seg: Double, toTheRight: Boolean,  newPathAmends: Seq[L.Modifier[L.SvgElement]]): ControlFlowPath = {
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    def xDir(dist: Double): Double = if (toTheRight) dist else -dist

    def addMiddleSection(path: SvgPathBuilder[Double]): SvgPathBuilder[Double] = {
      path
        .horizontalLineWithWidth(xDir(2 * seg))
        .lineToRel(Dimension[Double](xDir(seg), seg))
        .horizontalLineWithWidth(xDir(2 * seg))
    }
    var continuedPath = path.lastSegment.curPath
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

    path
      .changeLastPathBuilder(curPathBuilder => continuedPath)
      .appendNewSegmentFromScratch(PathSegment(secondPath, List(), true, newPathAmends))
  }

  private def handleFirstParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): ControlFlowPath = {
    extendAndAppendParentPath(path, curLineHeight, renderingConfig.controlSegmentSize, true, renderingConfig.amendFactory.activeFalseConditionControlFlowAmends)
  }

  private def handleSecondParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): ControlFlowPath = {
    extendAndAppendParentPath(path, curLineHeight, renderingConfig.controlSegmentSize, false,  renderingConfig.amendFactory.inactiveTrueConditionControlFlowAmends)
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    // change order of paths so children fetch the right parent path

    val leftToRightPath = handleFirstParentPath(cf.firstOpenPath, renderingInfo.renderingConfig, curLineHeight)
    val rightToLeftPath = handleSecondParentPath(cf.secondOpenPath, renderingInfo.renderingConfig, curLineHeight)

    cf
      .changeFirstOpenPath(_ => rightToLeftPath)
      .changeFirstOpenPath(_ => leftToRightPath)
    // .addDecoration(PathCrossOverlay(), centerPoint)
    // .addDecoration(ControlArrowCross(true, true, true), centerPoint)


  }


}
