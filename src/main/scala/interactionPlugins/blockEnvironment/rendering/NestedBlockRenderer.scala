package interactionPlugins.blockEnvironment.rendering

import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.composite.HorizontalAlignment.Left
import contentmanagement.webElements.svg.shapes.composite.VerticalAlignment.Center
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.*

import scala.collection.mutable


case class LineForRendering(lineNr: Int, segment: NestedBlockSegment, line: NestedBlockLine, controlFlowStack: List[ControlFlowShape]) {
  /*lazy val lineExprBox: BeShape = {
    val boxContent = line.expressionShape.map(List(_)).getOrElse(List())
    VBoxSameWidth(boxContent, false, Left, Center)
  }*/

  lazy val lineAllShapes: List[BeShape] = controlFlowStack ++ List(line.controlFlowShape) ++ line.expressionShape

  def lineHeight(renderingConfig: BeRenderingConfig) = lineAllShapes.map(_.displaySize(renderingConfig).height).max

  def lastControlFlowShapeCenterX(renderingConfig: BeRenderingConfig, controlFlowColumnWidths: List[Double]): Double = {
    val curStackSize = controlFlowStack.size
    val curStackWidth = controlFlowColumnWidths.slice(0, curStackSize).sum

    curStackWidth + line.controlFlowShape.displaySize(renderingConfig).width / 2
  }

  def expressionShapeRelativeOffsetX(renderingConfig: BeRenderingConfig, controlFlowColumnWidths: List[Double]): Double = {
    val curStackSize = controlFlowStack.size
    val curStackWidth = controlFlowColumnWidths.slice(0, curStackSize).sum

    val controlFlowWidth: Double = line.controlFlowShape.displaySize(renderingConfig).width
    val paddingWidth: Double = renderingConfig.paddingSmall.width
    curStackWidth + controlFlowWidth + paddingWidth
  }
}

case class NestedBlockRenderer(
                                segments: List[NestedBlockSegment]
                              ) {

  lazy val lastSegment: Option[NestedBlockSegment] = segments.lastOption

  lazy val lastLine: Option[NestedBlockLine] = lastSegment.flatMap(_.lines.lastOption)

  lazy val allLines: List[NestedBlockLine] = segments.flatMap(_.lines)

  lazy val allExpressionShapes: List[BeShape] = allLines.flatMap(_.expressionShape)

  def firstExpressionShapeOrHBox(usePadding: Boolean = false): BeShape =
    if (allExpressionShapes.size == 1) allExpressionShapes.head
    else HBoxSameHeight(allExpressionShapes, usePadding)

  def withAppendedSegment(segment: NestedBlockSegment): NestedBlockRenderer = {
    NestedBlockRenderer(segments ++ List(segment))
  }

  def withReplacedLastSegment(segment: NestedBlockSegment): NestedBlockRenderer = {
    NestedBlockRenderer(segments.init ++ List(segment))
  }

  def withAppendedLine(line: NestedBlockLine): NestedBlockRenderer = {
    if (lastSegment.isEmpty || lastLine.isEmpty || (lastLine.nonEmpty && lastLine.get.isStackChanging) || line.isStackChanging) {
      withAppendedSegment(NestedBlockSegment(List(line)))
    } else {
      withReplacedLastSegment(NestedBlockSegment(lastSegment.get.lines ++ List(line)))
    }
  }

  lazy val linesToRender: List[LineForRendering] = {

    val res = mutable.ListBuffer[LineForRendering]()
    val curStack: mutable.Stack[ControlFlowShape] = mutable.Stack()

    var globalLineNr: Int = 0
    for (curSegment <- segments) {
      for (curLine <- curSegment.lines) {
        if (curLine.popStackBefore) {
          curStack.pop()
        }
        val lineStack = curStack.toList

        res += LineForRendering(globalLineNr, curSegment, curLine, curStack.toList)
        globalLineNr += 1
        curLine.addToStackAfterwards.foreach(curStack.push)
      }
    }

    res.toList
  }

  private def getControlFlowStackColumnWidths(config: BeRenderingConfig): List[Double] = {
    val maxStackSize = linesToRender.map(_.controlFlowStack.size).max

    0.until(maxStackSize).map(curColumnNr => {
      linesToRender.flatMap(_.controlFlowStack.lift(curColumnNr)).map(_.displaySize(config).width).max
    }).toList
  }

  def expressionShapeWithoutIntendation: BeShape = {
    VBoxSameWidth(allExpressionShapes, false, HorizontalAlignment.Left, VerticalAlignment.Center)
  }


  def expressionShapeWithIntendation: BeShape = new BoxManualPositioning {

    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {

      val controlFlowColumnWidth: List[Double] = getControlFlowStackColumnWidths(config)
      var offsetY: Double = 0

      val res = mutable.ListBuffer[ManualPositionElement]()

      for (curLineToRender <- linesToRender) {
        if (curLineToRender.line.expressionShape.nonEmpty) {
          val offsetX = curLineToRender.expressionShapeRelativeOffsetX(config, controlFlowColumnWidth)
          //val width = curLineToRender.segment.getSegmentWidth(config) // todo block look?
          // val height = curLineToRender.lineHeight(config)
          val width = curLineToRender.line.expressionShape.get.displaySize(config).width
          val height = curLineToRender.line.expressionShape.get.displaySize(config).height
          res.addOne(ManualPositionElement(curLineToRender.line.expressionShape.get, Point[Double](offsetX, offsetY), Dimension[Double](width, height)))
        }
        offsetY += curLineToRender.lineHeight(config)
      }

      res.toList
    }
  }

  def controlFlowBackgroundShape: BeShape = controlFlowBasicBackgroundShape
  
  def controlFlowBasicBackgroundShape: BeShape = new BoxManualPositioning {

    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      val controlFlowColumnWidths = getControlFlowStackColumnWidths(config)
      val res = mutable.ListBuffer[ManualPositionElement]()

      var curStartY: Double = 0
      var curStartX: Double = 0
      var index = 0

      for (curLineToRender <- linesToRender) {
        index += 1

        curStartX = 0
        // stack
        val allControlShapesWithIndex = (curLineToRender.controlFlowStack :+ curLineToRender.line.controlFlowShape).zipWithIndex
        for ((curControlShape, curColumn) <- allControlShapesWithIndex) {
          val curCfRelOffset = new Point[Double](curStartX, curStartY)
          val curCfShapeDim = if (curColumn < controlFlowColumnWidths.size) {
            new Dimension[Double](controlFlowColumnWidths(curColumn), curLineToRender.lineHeight(config))
          } else {
            new Dimension[Double](curControlShape.displaySize(config).width, curLineToRender.lineHeight(config))
          }
          res.addOne(ManualPositionElement(curControlShape.shapeRenderBackground(config), curCfRelOffset, curCfShapeDim))
          curStartX += curCfShapeDim.width
        }
        curStartY += curLineToRender.lineHeight(config)
      }
      res.toList
    }
  }

  def controlFlowOverlayShape(renderingInfo: RenderingInformation): BeShape = {
    val controlFlowBuilder = getControlFlowBuilder(renderingInfo)
    val controlFlowOverlay = controlFlowBuilder.renderControlFlow()
    controlFlowOverlay
  }

  private def getControlFlowBuilder(renderingInfo: RenderingInformation): ControlFlowOverlayBuilder = {
    var res = ControlFlowOverlayBuilder(List(), List())

    val stackColumnWidths = getControlFlowStackColumnWidths(renderingInfo.renderingConfig)
    val (stackColumnCenter, stackWidth): (List[Double], Double) = {
      var offset: Double = 0
      val res = mutable.ListBuffer[Double]()
      for (curWidth <- stackColumnWidths) {
        res.addOne(offset + curWidth / 2)
        offset += curWidth
      }
      (res.toList, offset)
    }


    def printStatus(startStr: String): Unit = {
      println("    [INFO] NestedBlockRenderer::getControlFlowBuilder, " + startStr
        + ", lists: open=" + res.paths.count(_.curStatus == PathStatus.OPEN)
        + ", handled=" + res.paths.count(_.curStatus == PathStatus.HANDLED)
        + ", paused=" + res.paths.count(_.curStatus == PathStatus.PAUSED)
        + ", finished=" + res.paths.count(_.curStatus == PathStatus.FINISHED)
      )
    }

    var curOffsetY: Double = renderingInfo.renderingConfig.controlSegmentSize
    for (curLineToRender <- linesToRender) {
      val curCenterY = curOffsetY + curLineToRender.lineHeight(renderingInfo.renderingConfig) / 2
      curOffsetY += curLineToRender.lineHeight(renderingInfo.renderingConfig)
      for ((curControlFlowShape, curCenterX) <- curLineToRender.controlFlowStack.reverse.zip(stackColumnCenter)) {
        res = curControlFlowShape.renderControlFlow(res, renderingInfo, Point[Double](curCenterX, curCenterY), curLineToRender.lineHeight(renderingInfo.renderingConfig))
        //printStatus("Handled Stack Shape: " + curControlFlowShape.getClass.getSimpleName)
      }
      for (curControlFlowShape <- Some(curLineToRender.line.controlFlowShape)) {
        val curCenterX = curLineToRender.lastControlFlowShapeCenterX(renderingInfo.renderingConfig, stackColumnWidths)
        res = curControlFlowShape.renderControlFlow(res, renderingInfo, Point[Double](curCenterX, curCenterY), curLineToRender.lineHeight(renderingInfo.renderingConfig))
        //printStatus("Handled Flow Shape: " + curControlFlowShape.getClass.getSimpleName)
      }

      res = res.resetHandledToOpen()
      //printStatus("+++ Handled Line: " + curLineToRender.lineNr)
    }
    res
  }

  def withAppendedRenderer(other: NestedBlockRenderer): NestedBlockRenderer = {
    var res = this
    for (segment <- other.segments) {
      for (line <- segment.lines) {
        res = res.withAppendedLine(line)
      }
    }
    res
  }
}

object NestedBlockRenderer {

  def singleExpressionLineShapeWithInfo(allLines: List[NestedBlockLine], newControlFlowShape: ControlFlowShape, newExprShape: BeShape): NestedBlockRenderer = {
    val navShapes = allLines.flatMap(_.navShapes)
    val infoShapes = allLines.flatMap(_.infoShapes)
    val sideEffectShapes = allLines.flatMap(_.sideEffectShapes)
    val newLine = ExpressionLine(newControlFlowShape, newExprShape, infoShapes, navShapes, sideEffectShapes)

    NestedBlockRenderer(List(NestedBlockSegment(List(newLine))))
  }

  def empty(): NestedBlockRenderer = NestedBlockRenderer(List())

  sealed trait NestedBlockLine {

    def popStackBefore: Boolean

    def addToStackAfterwards: Option[ControlFlowShape]

    def isStackChanging: Boolean = popStackBefore || addToStackAfterwards.nonEmpty

    def controlFlowShape: ControlFlowShape

    def expressionShape: Option[BeShape]

    def infoShapes: List[BeShape]

    def navShapes: List[BeShape]

    def sideEffectShapes: List[BeShape]
  }

  case class ControlFlowLine(controlFlowShape: ControlFlowShape, expressionShape: Option[BeShape] = None) extends NestedBlockLine {

    override def popStackBefore: Boolean = false

    override def addToStackAfterwards: Option[ControlFlowShape] = None

    override def infoShapes: List[BeShape] = List()

    override def navShapes: List[BeShape] = List()

    override def sideEffectShapes: List[BeShape] = List()
  }

  case class ControlFlowReplaceLine(controlFlowShape: ControlFlowShape, expressionShape: Option[BeShape], addToStack: ControlFlowShape) extends NestedBlockLine {

    override def popStackBefore: Boolean = true

    override def addToStackAfterwards: Option[ControlFlowShape] = Some(addToStack)

    override def infoShapes: List[BeShape] = List()

    override def navShapes: List[BeShape] = List()

    override def sideEffectShapes: List[BeShape] = List()
  }

  case class ControlFlowIncreaseLine(controlFlowShape: ControlFlowShape, expressionShape: Option[BeShape] = None, addToStack: ControlFlowShape) extends NestedBlockLine {

    val addToStackAfterwards: Option[ControlFlowShape] = Some(addToStack)
    val popStackBefore: Boolean = false

    def infoShapes: List[BeShape] = List()

    def navShapes: List[BeShape] = List()

    def sideEffectShapes: List[BeShape] = List()
  }

  case class ControlFlowDecreaseLine(controlFlowShape: ControlFlowShape, expressionShape: Option[BeShape] = None) extends NestedBlockLine {

    val addToStackAfterwards: Option[ControlFlowShape] = None
    val popStackBefore: Boolean = true

    def infoShapes: List[BeShape] = List()

    def navShapes: List[BeShape] = List()

    def sideEffectShapes: List[BeShape] = List()
  }

  case class ExpressionLine(
                             controlFlowShape: ControlFlowShape,
                             exprShape: BeShape,
                             infoShapes: List[BeShape],
                             navShapes: List[BeShape],
                             sideEffectShapes: List[BeShape]
                           ) extends NestedBlockLine {

    val addToStackAfterwards: Option[ControlFlowShape] = None
    val popStackBefore: Boolean = false

    def expressionShape: Option[BeShape] = Some(exprShape)

  }

  case class NestedBlockSegment(
                                 lines: List[NestedBlockLine]
                               ) {

    def getSegmentWidth(renderingConfig: BeRenderingConfig): Double = {
      if (lines.nonEmpty) lines.flatMap(_.expressionShape).map(_.displaySize(renderingConfig).width).max else 0
    }

  }


}
