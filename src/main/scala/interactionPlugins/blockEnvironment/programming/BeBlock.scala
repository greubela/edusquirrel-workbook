package interactionPlugins.blockEnvironment.programming


import interactionPlugins.blockEnvironment.programming.blocks.BeMotionBlocks.{BeBlockForward, MotionBlock}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlockStarter, BeBlockValue}
import interactionPlugins.blockEnvironment.programming.visitor.BeBlockVisitor

trait BeBlock {

  def isFinished: Boolean

  def hasSideEffects: Boolean

  def evaluatesTo: BeDataType

  def children: List[BeBlock]

  def onVisiting[S](visitor: BeBlockVisitor[S]): S => S

  def visitBottomUp[S](visitor: BeBlockVisitor[S]): Unit = recursiveVisiting(visitor, false)

  def visitTopDown[S](visitor: BeBlockVisitor[S]): Unit = recursiveVisiting(visitor, true)

  private def recursiveVisiting[S](visitor: BeBlockVisitor[S], ownFirst: Boolean): Unit = {
    val handleOnVisiting = onVisiting(visitor)
    if (ownFirst) {
      visitor.updateState(handleOnVisiting(visitor.currentState))
      children.foreach(_.recursiveVisiting(visitor, ownFirst))
    } else {
      children.foreach(_.recursiveVisiting(visitor, ownFirst))
      visitor.updateState(handleOnVisiting(visitor.currentState))
    }
  }


}


abstract class BeBlockExtendable(val evaluatesTo: BeDataType) extends BeBlock {
  override def isFinished: Boolean = children.forall(_.isFinished)
}


object BeBlock {

  def sampleProgram(): BeBlockStarter = {
    val parBlock = BeBlockValue(BeDataType.Numeric, Some("123"))
    val moveBlock = BeBlockForward(parBlock)
    BeBlockStarter(moveBlock)
  }

}