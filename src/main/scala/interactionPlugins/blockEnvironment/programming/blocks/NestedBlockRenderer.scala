package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.shapes.composite.HorizontalAlignment.Left
import contentmanagement.webElements.svg.shapes.composite.VerticalAlignment.Center
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, VBoxSameWidth}
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blocks.NestedBlockRenderer.*

import scala.collection.mutable

/*case class NestedLineToRender(
                             controlFlowShapes: List[(ControlFlowShape, Bounds[Double])],
                             mainExpression: Option[(BeShape, Bounds[Double])],
                             ) extends BeShape{

}*/


case class NestedBlockRenderer(
                                segments: List[NestedBlockSegment]
                              ) {


  lazy val lastSegment: Option[NestedBlockSegment] = segments.lastOption

  lazy val lastLine: Option[NestedBlockLine] = lastSegment.flatMap(_.lines.lastOption)

  lazy val allLines: List[NestedBlockLine] = segments.flatMap(_.lines)

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

  lazy val linesWithControlFlowStack: List[(NestedBlockLine, List[ControlFlowShape])] = {
    val res = mutable.ListBuffer[(NestedBlockLine, List[ControlFlowShape])]()
    val curStack: mutable.Stack[ControlFlowShape] = mutable.Stack()
    for (curLine <- allLines) {
      if (curLine.popStackBefore) {
        curStack.pop()
      }
      res += ((curLine, curStack.toList))
      curLine.addToStackAfterwards.foreach(curStack.push)
    }
    res.toList
  }

  lazy val linesWithCurrentControlFlow: List[(NestedBlockLine, ControlFlowShape)] = {
    allLines.map(line => (line, line.controlFlowShape))
  }

  lazy val linesWithExpressionShapes: List[(NestedBlockLine, Option[BeShape])] = {
    allLines.map(line => (line, line.expressionShape))
  }

  lazy val linesWithMainShapeInfo: List[(NestedBlockLine, List[ControlFlowShape], ControlFlowShape, Option[BeShape])] = {
    linesWithControlFlowStack.zip(linesWithCurrentControlFlow).zip(linesWithExpressionShapes).map {
      case (((line, stack), curControlFlow), curExpression) => (line, stack, curControlFlow._2, curExpression._2)
    }
  }

  lazy val getShapeExpressions: BeShape = {
    VBoxSameWidth(allLines.flatMap(_.expressionShape), false, Left, Center)
  }

  lazy val getShapeControlFlowAndExpressions: BeShape = new BeShape.BeShapeComposite {

    def relativeOffsetsAndDimensions(config: BeRenderingConfig): List[(BeShape, Point[Double], Dimension[Double])] = {

      val linesWithHeight: List[(NestedBlockLine, List[ControlFlowShape], ControlFlowShape, Option[BeShape], Double)] = {
        linesWithMainShapeInfo.map((curLine, curStack, curControlFlow, curExprOp) => {
          val allShapesInLine = List(curControlFlow) ++ curStack ++ curExprOp
          val curHeight = allShapesInLine.map(_.displaySize(config).height).max
          (curLine, curStack, curControlFlow, curExprOp, curHeight)
        })
      }

      println("lines with height calculated, " + linesWithHeight.size + " lines: " + linesWithHeight.map(_._5))

      val controlFlowStackColumnWidths: List[Double] = {
        val maxStackSize = linesWithMainShapeInfo.map(_._2.size).max
        val stackColumns: List[List[ControlFlowShape]] = {
          val lists = linesWithMainShapeInfo.map(_._2)
          0.until(maxStackSize).map(curColumnNr => lists.flatMap(curList => {
            curList.lift(curColumnNr)
          })).toList
        }
        stackColumns.map(_.map(_.displaySize(config).width).max)
      }

      println("control flow stack column width calculated, " + controlFlowStackColumnWidths.size + " columns: " + controlFlowStackColumnWidths)

      // todo curControlFlow && associatedExpression with box layout (same width in segment, calc beforehand!!)

      val res = mutable.ListBuffer[(BeShape, Point[Double], Dimension[Double])]()

      var curStartY: Double = 0
      var curStartX: Double = 0
      var index = 0

      for ((curLine, curControlStack, curControlShape, curExprOp, curLineHeight) <- linesWithHeight) {
        println("line " + index + ", stack size " + curControlStack.size + ": " + curLine.getClass.getSimpleName)
        index += 1

        curStartX = 0
        // stack
        for ((curControlStackShape, curColumn) <- curControlStack.zipWithIndex) {
          val curStackCfRelOffset = new Point[Double](curStartX, curStartY)
          val curStackCfShapeDim = new Dimension[Double](controlFlowStackColumnWidths(curColumn), curLineHeight)
          res.addOne((curControlStackShape, curStackCfRelOffset, curStackCfShapeDim))
          curStartX += curStackCfShapeDim.width
        }
        // most recent control flow shape
        val cfRelOffset = new Point[Double](curStartX, curStartY)
        val cfShapeDim = new Dimension[Double](curControlShape.displaySize(config).width, curLineHeight)
        res.addOne((curControlShape, cfRelOffset, cfShapeDim))
        println("added most recent control flow shape: " + cfRelOffset + "/" + cfShapeDim + " (" + curStartX + ")")
        curStartX += cfShapeDim.width
        println("-> (" + curStartX + ")")

        // expression of present
        for (curExprShape <- curExprOp) {
          val exprRelOffset = new Point[Double](curStartX, curStartY)
          val exprShapeDim = new Dimension[Double](curExprShape.displaySize(config).width, curLineHeight)
          res.addOne((curExprShape, exprRelOffset, exprShapeDim))
          curStartX += exprShapeDim.width
        }
        curStartY += curLineHeight
      }
      res.toList
    }

    override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      val renderedChildren = relativeOffsetsAndDimensions(rendererConfig).map((curShape, curRelOffset, curDim) => {
        val curShapeBounds = bounds.startPoint.moveWithDimension(curRelOffset.asDimension).withDimension(curDim)
        curShape.render(rendererConfig, curShapeBounds)
      })
      AppGroupSvgElement(renderedChildren)
    }

    override def children: List[BeShape] = {
      linesWithMainShapeInfo.flatMap((curLine, curStack, curControlFlow, curExprOp) => List(curControlFlow) ++ curStack ++ curExprOp)
    }

    override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
      val bounds = relativeOffsetsAndDimensions(rendererConfig).map((curShape, curRelOffset, curDim) => curRelOffset.withDimension(curDim))
      Dimension(bounds.map(_.endX).max, bounds.map(_.endY).max + rendererConfig.controlSegmentSize)
    }


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

  }


}
