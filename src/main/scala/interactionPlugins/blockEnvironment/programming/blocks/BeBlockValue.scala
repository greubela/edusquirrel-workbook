package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Dimension
import interactionPlugins.blockEnvironment.programming.visitor.{BeBlockVisitor, CalculateSizeVisitor, ToPythonStringVisitor}
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeDataType}

case class BeBlockValue(evaluatesTo: BeDataType, associatedValue: Option[String] = None, hasSideEffects: Boolean = false) extends BeBlock {
  override val isFinished: Boolean = associatedValue.nonEmpty

  override def children: List[BeBlock] = List()

  def onVisiting[S](visitor: BeBlockVisitor[S]): S => S = {
    visitor match {
      case ToPythonStringVisitor(importString: String)  => (s => s) // done by parent
      case CalculateSizeVisitor(font: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double]) => state => {
        state.add(this, font.measureText(associatedValue.getOrElse("[  ]")).increaseSize(paddingSmall))
      }
      case _ => ???
    }
  }

}
