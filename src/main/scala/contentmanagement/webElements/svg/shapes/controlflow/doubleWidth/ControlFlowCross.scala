package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.*
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowCross.*
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.decorations.{ControlArrowCross, PathCrossOverlay}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class ControlFlowCross() extends ControlFlowShapeDoubleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, true)))

  override def minHeightInSegments: Int = 5

  /*
  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val rwa = RightToLeft().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lwa = LeftToRight().addAmends(rendererConfig.amendFactory.inactiveControlFlowAmends)
    val pwa = PathCrossOverlay().addAmends(rendererConfig.amendFactory.crossSymbolControlFlowAmends)

    val stack = ShapeStack(List(lwa, rwa, pwa), HorizontalAlignment.Left, VerticalAlignment.Top)

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())
  }
*/

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
