package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.language.*
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.usage.{BeFunctionCall, BeUseValueLiteral}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.*

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeExpressionTree = Tree[NodeBasedTreePosition, (BeChildRole, BeExpression, BeScope)]

type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]
type BeExpressionContext = TreeStructureContext[NodeBasedTreePosition, (BeChildRole, BeExpression, BeScope)]

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

}

object BeProgram {

  def starterTree(config: BeDisplayConfig): BeProgram = {
    val starter = BeStartProgram(None).createBlock(config)
    val tree = NodeBasedTreeImpl.empty[BeBlock]()
    val starterTree = tree.addAsLastChild(tree.rootPosition, starter)
    fromBlockTree(config, starterTree)
  }

  def apply(config: BeDisplayConfig, blockTree: BeBlockTree): BeProgram = {
    fromBlockTree(config, blockTree)
  }

  def fromExpression(config: BeDisplayConfig, expression: BeExpression): BeProgram = {
    val expressionTree: BeExpressionTree = expression.recToTree(config, NoRole, BeScope.GlobalScope())
    fromExpressionTree(config, expressionTree)
  }

  def fromExpressionTree(config: BeDisplayConfig, expressionTree: BeExpressionTree): BeProgram = {
    if (expressionTree.isEmpty) {
      starterTree(config)
    }

    val blockTree: BeBlockTree = expressionTree.mapWithStructure(structure => {
      val curPos = structure.curPosition
      val curRole = structure.curValue._1
      val curExpression = structure.curValue._2
      val curScope = structure.curValue._3
      curExpression.createBlock(config, BeChildPosition(curPos, curRole, curScope))
    })
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
        structure.curValue.positionAsChild.roleInParent,
        structure.curValue.calcAssociatedExpression(structure),
        structure.curValue.positionAsChild.curScope))
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

    val literals = variables.zip(parValues).map((curVar, curVal) => BeUseValueLiteral(curVal))
    val expression: BeExpression =
      BeStartProgram(
        BeSequence(
          false,
          List(BeFunctionCall(
            BeDefineFunction(
              BeFunctionSignature(
                LanguageMap.universalMap(functionName),
                variables,
                None
              ),
              BeExpression.pass
            ),
            literals
          )
          )
        )
      )

    fromExpression(displayConfig, expression)
  }


  def createOneParFunc(displayConfig: BeDisplayConfig, functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram = {
    createSimpleFunc(displayConfig, functionName, List(parName), List(parType), List(valueString))
  }

  // todo: To functions, BeBlockTree -> vm representation // vm representation -> BeBlockTree


  def miniProgramExpression(): BeExpression = {

    val forwardName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.German -> "vorwärts",
      AppLanguage.English -> "forward"
    ))

    val parameter: BeDefineVariable = BeDefineVariable(
      LanguageMap.mapBasedLanguageMap(Map(
        AppLanguage.German -> "distanz",
        AppLanguage.English -> "distance")
      ),
      Set(BeDataType.Numeric))

    val forwardFunc = BeDefineFunction(
      BeFunctionSignature(forwardName, List(parameter), None),
      BeExpression.pass
    )

    BeStartProgram(
      BeSequence.optionalUnitBody(List(
        BeFunctionCall(
          forwardFunc,
          List(
            parameter.toUseLiteralWithContext("100")
          )
        ),
        BeFunctionCall(
          forwardFunc,
          List(
            parameter.toUseLiteralWithContext("10x00")
          )
        )
      )
      )
    )

  }

  def miniProgram(displayConfig: BeDisplayConfig): BeProgram = {
    fromExpression(displayConfig, miniProgramExpression())
  }


}
