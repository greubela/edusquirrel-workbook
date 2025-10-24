package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.{NodeBasedTreeImpl, NodeBasedTreePosition}
import contentmanagement.model.language.LanguageMap
import interactionPlugins.blockEnvironment.programming.blocks.displayUtility.BeBlockDisplayMissingValue
import interactionPlugins.blockEnvironment.programming.blocks.function.BeFunction
import interactionPlugins.blockEnvironment.programming.blocks.traits.BeBlockLogic
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockUseLiteral
import interactionPlugins.blockEnvironment.programming.connection.FunctionParameter
import interactionPlugins.blockEnvironment.programming.{BeDataType, BeProgram}

case class HtmlBlockLibrary() {


}

object HtmlBlockLibrary {


  protected[elements] def functionWithOnePar(functionName: String, parType: BeDataType, numVal: String): BeProgram = {
    val numPar = FunctionParameter(0, parType)
    val numBlock = BeBlockUseLiteral(numPar, numVal, parType)

    val func = BeFunction(LanguageMap.universalMap(functionName), List(numPar), List())
    val funcBlock = func.toCallBlock()

    var tree: Tree[NodeBasedTreePosition, BeBlockLogic] = NodeBasedTreeImpl.empty[BeBlockLogic]()
    tree = tree.addAsLastChild(tree.rootPosition, funcBlock)
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), numBlock)
    BeProgram(tree)
  }



}