package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Dimension
import interactionPlugins.blockEnvironment.programming.BeDataType.BlockDescription
import interactionPlugins.blockEnvironment.programming.visitor.*
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeDataType}


case class BeBlockTextElement(text: String) extends BeBlock {

  override def isFinished: Boolean = true

  override def hasSideEffects: Boolean = false

  override def evaluatesTo: BeDataType = BlockDescription

  override def children: List[BeBlock] = List()

  def onVisiting[S](visitor: BeBlockVisitor[S]): S => S = {
    visitor match {
      case ToPythonStringVisitor(importString: String) => (s => s) // done by parent
      case CalculateSizeVisitor(font: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double]) => state => {
        state.add(this, font.measureText(text).increaseSize(paddingSmall))
      }
      case _ => ???
    }
  }

}
