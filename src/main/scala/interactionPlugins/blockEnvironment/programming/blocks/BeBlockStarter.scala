package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Dimension
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeBlockExtendable}
import interactionPlugins.blockEnvironment.programming.BeDataType.Unit
import interactionPlugins.blockEnvironment.programming.visitor.{BeBlockVisitor, CalculateSizeVisitor, ToPythonStringVisitor}

case class BeBlockStarter(child: BeBlock) extends BeBlockExtendable(Unit) {

  override def hasSideEffects: Boolean = child.hasSideEffects

  override def children: List[BeBlock] = List(child)

  override def onVisiting[S](visitor: BeBlockVisitor[S]): S => S =
    visitor match {
      case ToPythonStringVisitor(importString: String) => (s => s)
      case CalculateSizeVisitor(font: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double]) => state => {
        val childDim = state.map(child)
        state.add(this, new Dimension[Double](childDim.width, 50))
      }
      case _ => ???
    }
}



