package contentmanagement.webElements.svg.shapes.special.nested

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeComposite
import contentmanagement.webElements.svg.shapes.composite.{BoxManualPositioning, ManualPositionElement}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowShapeDoubleWidth
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

import scala.collection.mutable

trait NestedControlStructureShape extends BeShapeComposite with ControlFlowAndExpressionShape {

  def leftColumnWidth(config: BeRenderingConfig): Double

  def totalControlFlowWidth(config: BeRenderingConfig): Double

  def getControlFlowChangeElements: List[ControlFlowShape]

  def getControlFlowExpressionElements: List[Option[BeShape]]

  def getBodiesBetweenControlFlowChanges: List[List[BeShape]]

  private def rightControlFlowWith(config: BeRenderingConfig): Double = totalControlFlowWidth(config) - leftColumnWidth(config)

  private def getBodyHeights(rendererConfig: BeRenderingConfig): List[Double] = {
    getBodiesBetweenControlFlowChanges.map(curBody => curBody.map(_.displaySize(rendererConfig).height).sum)
  }

  private def controlFlowHeights(rendererConfig: BeRenderingConfig): List[Double] = {
    getControlFlowChangeElements.zip(getControlFlowExpressionElements).map((controlFlow, expression) => {

      val cf: Double = controlFlow.displaySize(rendererConfig).height
      val ex: Option[Double] = expression.map(_.displaySize(rendererConfig).height)
      (ex.toList ++ List(cf)).max
    })
  }

  def getPositionedControlFlowExpressionShapes(rendererConfig: BeRenderingConfig): List[ManualPositionElement] = {
    var curOffsetY: Double = 0
    val bodyHeights: List[Double] = getBodyHeights(rendererConfig)
    val res = mutable.ListBuffer[ManualPositionElement]()
    for (trip <- getControlFlowExpressionElements.zip(controlFlowHeights(rendererConfig).zip(bodyHeights))) {
      val (controlFlowExpressionOp, curControlFlowHeight, followingBodyHeight): (Option[BeShape], Double, Double) = (trip._1, trip._2._1, trip._2._2)
      if (controlFlowExpressionOp.nonEmpty) {
        val curExpressionDim = controlFlowExpressionOp.get.displaySize(rendererConfig)
        val extraHeight = curControlFlowHeight - curExpressionDim.height
        res.addOne(ManualPositionElement(controlFlowExpressionOp.get, Point[Double](totalControlFlowWidth(rendererConfig), curOffsetY + extraHeight / 2), curExpressionDim))
      }
      curOffsetY += curControlFlowHeight
      curOffsetY += followingBodyHeight
    }
    res.toList
  }

  def getPositionedExpressionShapes(rendererConfig: BeRenderingConfig): List[ManualPositionElement] = {

    val res = mutable.ListBuffer[ManualPositionElement]()

    val cfHeights: List[Double] = controlFlowHeights(rendererConfig)
    val bodyHeights: List[Double] = getBodyHeights(rendererConfig)
    val seg: Double = rendererConfig.controlSegmentSize

    var curOffsetY: Double = 0
    for ((curControlFlowHeight, curBodyShapes) <- cfHeights.zip(getBodiesBetweenControlFlowChanges)) {

      curOffsetY += curControlFlowHeight
      for (curBodyShape <- curBodyShapes) {
        val shapeDim = curBodyShape.displaySize(rendererConfig)
        res.addOne(ManualPositionElement(curBodyShape, new Point(leftColumnWidth(rendererConfig), curOffsetY), shapeDim))
        curOffsetY += shapeDim.height
      }

    }

    res.toList

  }

  def drawBackground(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
    val cfHeights: List[Double] = controlFlowHeights(rendererConfig)
    val bodyHeights: List[Double] = getBodyHeights(rendererConfig)

    val seg: Double = rendererConfig.controlSegmentSize
    val extraWidth = bounds.width - totalControlFlowWidth(rendererConfig)

    //top
    var res = SvgPathBuilder.mutableBuilder(bounds.startPoint)
      .addControlFlowConnector(seg)
      .horizontalLineWithWidth(rightControlFlowWith(rendererConfig))
      .horizontalLineWithWidth(extraWidth)

    for ((curControlFlowHeight, curBodyHeight) <- cfHeights.zip(bodyHeights)) {
      res = res
        .verticalLineWithHeight(curControlFlowHeight)
        .horizontalLineWithWidth(-extraWidth)
        .addControlFlowConnector(-seg, true)
        .verticalLineWithHeight(curBodyHeight)
        .addControlFlowConnector(seg)
        .horizontalLineWithWidth(extraWidth)
    }

    for (curControlFlowHeight <- cfHeights.lastOption.toList) {
      res = res
        .verticalLineWithHeight(curControlFlowHeight)
        .horizontalLineWithWidth(-extraWidth)
        .horizontalLineWithWidth(-rightControlFlowWith(rendererConfig))
        .addControlFlowConnector(-seg, true)
        .closePath()
    }
    res
  }


  private def positionedChildren(rendererConfig: BeRenderingConfig): List[ManualPositionElement] = {
    getPositionedExpressionShapes(rendererConfig) ++ getPositionedControlFlowExpressionShapes(rendererConfig)
  }

  private def createChildrenBox(rendererConfig: BeRenderingConfig): BoxManualPositioning = new BoxManualPositioning {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = positionedChildren(rendererConfig)
  }

  def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val cfHeights: List[Double] = controlFlowHeights(rendererConfig)
    val bodyHeights: List[Double] = getBodyHeights(rendererConfig)
    val totalHeight = cfHeights.sum + bodyHeights.sum

    createChildrenBox(rendererConfig).displaySize(rendererConfig).ensureWidth(totalControlFlowWidth(rendererConfig) * 2).ensureHeight(totalHeight)
  }

  def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val childrenBox = createChildrenBox(rendererConfig).render(rendererConfig, bounds)
    val background = drawBackground(rendererConfig, bounds).toFixedDimensionShape.addAmends(rendererConfig.amendFactory.defaultControlFlowBackgroundAmend).render(rendererConfig, bounds)

    AppDecoratedSvgElement(background, List(childrenBox), List())
  }



  def onlyControlFlowShape: Option[BeShape] = None

  def onlyExpressionShape: Option[BeShape] = None


}
