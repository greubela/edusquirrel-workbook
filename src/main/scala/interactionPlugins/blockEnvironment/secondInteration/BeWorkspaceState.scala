package interactionPlugins.blockEnvironment.secondInteration

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal
import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockFunctionDefinition
import interactionPlugins.blockEnvironment.programming.connection.{BeConnectionRole, FunctionBody}

class BeWorkspaceState(initialProgram: BeProgram) {

  private val programVar: Var[BeProgram] = Var(initialProgram)

  def programSignal: Signal[BeProgram] = programVar.signal

  def programNow(): BeProgram = programVar.now()

  def setProgram(program: BeProgram): Unit = programVar.set(program)

  def updateProgram(modifier: BeProgram => BeProgram): Unit = programVar.update(modifier)

  def insertPaletteEntry(entry: BePaletteEntry): Unit = {
    appendBlock(entry.instantiate(FunctionBody))
  }

  def appendBlock(block: BeBlock, role: BeConnectionRole = FunctionBody): Unit = {
    val _ = role
    programVar.update(program => appendBlockInternal(program, block))
  }

  private def appendBlockInternal(program: BeProgram, block: BeBlock): BeProgram = {
    val tree = program.tree
    val (preparedTree, parentPosition) = ensureFunctionBody(tree)
    val updatedTree = preparedTree.addChild(parentPosition, block)
    BeProgram(updatedTree)
  }

  private def ensureFunctionBody(tree: Tree[BeBlock, NodeBasedTreePosition]): (Tree[BeBlock, NodeBasedTreePosition], NodeBasedTreePosition) = {
    val maybeFunction = tree.getChildren(tree.rootPosition).headOption
    maybeFunction match {
      case Some(pos) => (tree, pos)
      case None =>
        val withRoot = tree.addChild(tree.rootPosition, BeBlockFunctionDefinition.starterBlock())
        (withRoot, withRoot.rootPosition.forChild(0))
    }
  }
}
