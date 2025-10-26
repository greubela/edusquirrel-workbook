package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.language.*
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.expressions.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockStarter
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.*

import scala.collection.mutable

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]

case class BeProgram(logicTree: BeBlockTree) {

  def treeWithChangedRootRoles(newRole: BeChildRole): BeBlockTree = {
    val res: BeBlockTree = logicTree.mapWithContext[BeBlock](context => {
      if (context.curPosition.level == 1) context.curValue.changeRole(newRole)
      else context.curValue
    })
    res
  }



  def getExpressions(): Tree[NodeBasedTreePosition, BeExpression] = {
    logicTree.mapWithStructure(structure => structure.curValue.calcAssociatedExpression(structure))
  }



  /*def toPythonString: String = {
    val res = logicTree.mapWithContext[String](context => context.curValue.toCode(Python, context))
    res.getData(res.rootPosition.forChild(0)).get
  }*/

  /*
  private def structureToRoleMap(structure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Map[BeBlockRole, (BeBlockValue, Int)] = {
    val childrenWithIndex = structure.childrenValues.zipWithIndex
    val valueChildrenWithIndex = childrenWithIndex.
      filter(curPair => curPair._1.isInstanceOf[BeBlockValue]).
      map(curPair => (curPair._1.asInstanceOf[BeBlockValue], curPair._2))

    val valueMap = valueChildrenWithIndex.map(curPair => curPair._1.roleInParent -> curPair).toMap
    valueMap
  }*/


}


object BeProgram {

  def createSimpleFunc(displayConfig: BeDisplayConfig, functionName: String, parNames: List[String], parTypes: List[BeDataType], parValues: List[String]): BeProgram = {

    val variables: List[BeDefineVariable] = parNames.zip(parTypes).zipWithIndex.map((tup, curIndex) => {
      val (curName, curType) = tup
      BeDefineVariable(LanguageMap.universalMap(curName), Set(curType))
    })

    val signature = BeFunctionSignature(LanguageMap.universalMap(functionName), variables, None)
    val funcDef = BeDefineFunction(signature, BeExpression.pass)

    val literals = variables.zip(parValues).map((curVar, curVal) => BeUseValueLiteral(curVal))

    val function = BeFunctionCall(funcDef, literals)
    val funcBlock = function.createBlock(displayConfig, NoRole)

    var tree: BeBlockTree = NodeBasedTreeImpl.empty[BeBlock]()
    tree = tree.addAsLastChild(tree.rootPosition, funcBlock)

    variables.zip(parValues).zipWithIndex.foreach((tup, curIndex) => {
      val (curVar, curVal) = tup
      val role = FunctionParameter(curIndex)
      val curLiteralBlock = curVar.toUseLiteralBlock(curVal)
      tree = tree.addAsLastChild(tree.rootPosition.forChild(0), curLiteralBlock)
    })

    BeProgram(tree)
  }


  def createOneParFunc(displayConfig: BeDisplayConfig, functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram = {
    createSimpleFunc(displayConfig, functionName, List(parName), List(parType), List(valueString))

  }

  // todo: To functions, BeBlockTree -> vm representation // vm representation -> BeBlockTree


  def miniProgram(): BeProgram = {
    val displayConfig = BeDisplayConfig.default()
    val forwardH = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "100")
    val forwardT = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "10x00")
    val forwardM = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "1000")

    val forwardExtra = forwardT.logicTree.getData(forwardT.logicTree.rootPosition.forChild(0)).get.changeRole(ExpressionInBody(2))

    var tree: BeBlockTree = NodeBasedTreeImpl.empty[BeBlock]()
    tree = tree.addAsLastChild(tree.rootPosition, BeBlockStarter())
    tree = tree.addAsLastChild(
      tree.rootPosition.forChild(0), BeSequence(List(),
        true,
        Some(Set(BeDataType.Unit))).createBlock(displayConfig, BodySequence()))
    tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardH.treeWithChangedRootRoles(ExpressionInBody(0)))
    tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardT.treeWithChangedRootRoles(ExpressionInBody(1)))
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardExtra)

    BeProgram(tree)
  }


}
