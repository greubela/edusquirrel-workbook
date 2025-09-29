package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Dimension
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.visitor.*

object BeMotionBlocks {

  abstract class MotionBlock() extends BeBlockExtendable(BeDataType.Unit) {
    override def hasSideEffects: Boolean = true
  }

  case class BeBlockForward(valueChild: BeBlockValue) extends MotionBlock() {

    override val children = List(
      BeBlockTextElement("forward"),
      valueChild,
      BeBlockTextElement("degrees"))

    def onVisiting[S](visitor: BeBlockVisitor[S]): S => S = {
      visitor match {
        case ToPythonStringVisitor(importString: String) =>
          _
            .appendNextLine("turtle.forward")
            .appendParameters(List(valueChild.associatedValue.getOrElse("[missing]")))

        case CalculateSizeVisitor(font: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double]) => state => {
          val childrenDims = children.map(visitor.currentState.map.get(_))
          val childrenWidthSum = childrenDims.map(_.get.width).sum
          val childrenHeightMax = childrenDims.map(_.get.height).max
          val resDim = Dimension[Double](childrenWidthSum, childrenHeightMax)
          state.add(this, resDim.increaseSize(paddingBig))
        }

        case _ => ???
      }
    }

  }

}