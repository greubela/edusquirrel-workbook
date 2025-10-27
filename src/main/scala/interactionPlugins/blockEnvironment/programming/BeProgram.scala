package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.language.*
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.expressions.defining.{BeDefineFunction, BeDefineVariable, BeStartProgram}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockStarter

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeExpressionTree = Tree[NodeBasedTreePosition, (BeChildRole, BeExpression)]

type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]
type BeExpressionContext = TreeStructureContext[NodeBasedTreePosition, (BeChildRole, BeExpression)]

case class BeProgram(displayConfig: BeDisplayConfig, blockTree: BeBlockTree, expressionTree: BeExpressionTree) {

  def canInsertAtPosition(insertAtPosition: NodeBasedTreePosition, newProgram: BeProgram): Boolean = {
    false
  }

  def treeWithChangedRootRoles(newRole: BeChildRole): BeBlockTree = {
    val res: BeBlockTree = blockTree.mapWithContext[BeBlock](context => {
      if (context.curPosition.level == 1) context.curValue.changeRole(newRole)
      else context.curValue
    })
    res
  }

  lazy val asExpression: BeExpression = BeProgram.expressionTreeToExpression(expressionTree)

  override val toString: String = {
    "BeDraggingEvent(BeBlockTree with size " + blockTree.size
      + ", BeExpressionTree with size " + expressionTree.size + ") corresponding to expression:\n"
      + asExpression
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

  def starterTree(config: BeDisplayConfig): BeProgram = {
    val starter = BeBlockStarter()
    val tree = NodeBasedTreeImpl.empty[BeBlock]()
    val starterTree = tree.addAsLastChild(tree.rootPosition, starter)
    fromBlockTree(config, starterTree)
  }

  def apply(config: BeDisplayConfig, blockTree: BeBlockTree): BeProgram = {
    fromBlockTree(config, blockTree)
  }

  //def apply(expressionTree: BeExpressionTree): BeProgram = fromExpressionTree(expressionTree)

  def fromExpression(config: BeDisplayConfig, expression: BeExpression): BeProgram = {
    val expressionTree: BeExpressionTree = expression.recToTree(config, NoRole)
    fromExpressionTree(config, expressionTree)
  }

  def fromExpressionTree(config: BeDisplayConfig, expressionTree: BeExpressionTree): BeProgram = {
    if (expressionTree.isEmpty) {
      starterTree(config)
    }
    /*
    val firstRootPos: NodeBasedTreePosition = expressionTree.rootPosition.forChild(0)
    val firstRoot = expressionTree.getData(firstRootPos).get
    val reCalcedExpressionTree: BeExpresi = firstRoot._2.recToTree(config, firstRoot._1)
*/
    val blockTree: BeBlockTree = expressionTree.map((curRole, curExpression) => curExpression.createBlock(config, curRole))
    BeProgram(config, blockTree, expressionTree)
  }

  def expressionTreeToExpression(expressionTree: BeExpressionTree): BeExpression = {
    if (expressionTree.isEmpty) BeExpression.NoOp
    else expressionTree.getData(expressionTree.rootPosition.forChild(0)).get._2
  }

  def blockTreeToExpression(blockTree: BeBlockTree): BeExpression = {
    if (blockTree.isEmpty) {
      BeExpression.NoOp
    }
    val expressionTree: BeExpressionTree = {
      blockTree.mapWithStructure(structure => (
        structure.curValue.roleInParent,
        structure.curValue.calcAssociatedExpression(structure)))
    }
    expressionTree.getData(expressionTree.rootPosition.forChild(0)).get._2
  }

  def fromBlockTree(displayConfig: BeDisplayConfig, blockTree: BeBlockTree): BeProgram = {
    if (blockTree.isEmpty) {
      starterTree(displayConfig)
    }
    val expression = blockTreeToExpression(blockTree)
    fromExpression(displayConfig, expression)
  }


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
      val curLiteralBlock = curVar.toUseLiteralWithContext(curVal).createBlock(displayConfig, role)
      tree = tree.addAsLastChild(tree.rootPosition.forChild(0), curLiteralBlock)
    })

    fromBlockTree(displayConfig, tree)
  }


  def createOneParFunc(displayConfig: BeDisplayConfig, functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram = {
    createSimpleFunc(displayConfig, functionName, List(parName), List(parType), List(valueString))
  }

  // todo: To functions, BeBlockTree -> vm representation // vm representation -> BeBlockTree


  def miniProgramExpression(): BeExpression = {

    val forward: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.German -> "vorwärts",
      AppLanguage.English -> "forward"
    ))

    val parameter: BeDefineVariable = BeDefineVariable(
      LanguageMap.mapBasedLanguageMap(Map(
        AppLanguage.German -> "distanz",
        AppLanguage.English -> "distance")
      ),
      Set(BeDataType.Numeric))


      BeStartProgram(
        BeSequence.optionalUnitBody(List(
          BeFunctionCall(
            BeDefineFunction(
              BeFunctionSignature(forward, List(parameter), None),
              BeExpression.pass
            ),
            List(
              parameter.toUseLiteralWithContext("100")
            )
          )
        )
        )
      )
  }


  def miniProgram(displayConfig: BeDisplayConfig): BeProgram = {

    fromExpression(displayConfig, miniProgramExpression())
    /*val forwardH = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "100")
    val forwardT = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "10x00")
    val forwardM = createOneParFunc(displayConfig, "forward", "distance", BeDataType.Numeric, "1000")

    val forwardExtra = forwardT.blockTree.getData(forwardT.logicTree.rootPosition.forChild(0)).get.changeRole(ExpressionInBody(2))

    var tree: BeBlockTree = NodeBasedTreeImpl.empty[BeBlock]()
    tree = tree.addAsLastChild(tree.rootPosition, BeBlockStarter())
    tree = tree.addAsLastChild(
      tree.rootPosition.forChild(0), BeSequence(List(),
        true,
        Some(Set(BeDataType.Unit))).createBlock(displayConfig, BodySequence()))
    tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardH.treeWithChangedRootRoles(ExpressionInBody(0)))
    tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardT.treeWithChangedRootRoles(ExpressionInBody(1)))
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(0), forwardExtra)

    fromBlockTree(displayConfig, tree)*/
  }


}
