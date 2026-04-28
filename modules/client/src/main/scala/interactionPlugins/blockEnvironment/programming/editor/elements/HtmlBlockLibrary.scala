package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*


case class HtmlBlockLibrary() {


}

object HtmlBlockLibrary {
  
  /*protected[elements] def functionWithOnePar(functionName: String, parType: BeDataType, numVal: String): BeProgram = {
    val numPar = FunctionParameter(0, parType)
    val numBlock = BeBlockDefineVariable(numPar, numVal, parType)

    val func = BeFunction(LanguageMap.universalMap(functionName), List(numPar), List())
    val funcBlock = func.toCallBlock()

    var tree: Tree[NodeBasedTreePosition, BeBlockLogic] = NodeBasedTreeImpl.empty[BeBlockLogic]()
    tree = tree.addAsLastChild(tree.rootPosition, funcBlock)
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), numBlock)
    BeProgram(tree)
  }*/
  
}