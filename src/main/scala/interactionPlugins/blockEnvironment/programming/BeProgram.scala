package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.language.*
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.parsing.python.PythonParser
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.*
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockPlaceholder
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeExpressionTree = Tree[NodeBasedTreePosition, BeExpressionNode]

type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]
type BeExpressionContext = TreeStructureContext[NodeBasedTreePosition, BeExpressionNode]

type BeBlockRenderingTree = Tree[NodeBasedTreePosition, (BeExpressionNode, BeBlock)]
type BeBlockRenderingContext = TreeStructureAndExecutionContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock), NestedBlockRenderer]

case class BeProgram(fullProgram: BeExpression) {

  def expressionTree(displayConfig: BeTreeDisplayConfig) = fullProgram.recToTree(displayConfig.displayPlaceholders, BeChildPosition(NoRole, BeScope.GlobalScope()))

  def blockRenderingTree(displayConfig: BeTreeDisplayConfig): BeBlockRenderingTree = expressionTree(displayConfig).mapWithStructure(structure => {
    structure.curValue match {
      case BeExtensionPoint(isRequired, childPos, dataType) => {
        (structure.curValue, BeBlockPlaceholder(structure.curValue.asInstanceOf[BeExtensionPoint]))
      }
      case BeExpressionReference(childPos, expression) => {
        (structure.curValue, expression.createBlock())
      }
    }
  })

  /*
  def withInsertions(additionMap: Map[BeExtensionPoint, BeExpression]): BeProgram = {
    val reparsedExpression = expressionTree.mapWithContext[Option[(BeChildPosition, BeExpression)]](context => {
      context.curValue match {
        case BeExtensionPoint(isRequired, childPos, dataType) => {
          val replaceWith = additionMap.get(context.curValue.asInstanceOf[BeExtensionPoint])
          if (replaceWith.nonEmpty && dataType.canTakeValuesFrom(replaceWith.get.canEvaluateTo).possibleWithoutSyntaxErrors) Some((childPos, replaceWith.get))
          else None
        }
        case BeExpressionReference(childPos, expression) => {
          val childrenResults: List[(BeChildRole, BeExpression)] = context.accessChildrenResults.flatten.map(tup => (tup._1.roleInParent, tup._2))
          Some((childPos, expression.withReplacedChildren(childrenResults)))
        }
      }
    })

    val rootOp = reparsedExpression.getData(reparsedExpression.rootPosition.forChild(0)).get
    val newExpr = rootOp.map(_._2).getOrElse(BeExpression.pass)
    BeProgram(newExpr)
  }*/

  /*
      .flatMap(curChild => curChild match {
      case
        if (!additionMap.contains(curChild.asInstanceOf[BeExtensionPoint])) {
          None
        } else {
          val exprToAdd = additionMap(curChild.asInstanceOf[BeExtensionPoint])
          if ((exprToAdd.canEvaluateTo).possibleWithoutSyntaxErrors) {
            Some((childPos, exprToAdd))
          } else {
            None
          }
        }
      case BeExpressionReference(childPos, expression) => Some((childPos, expression))
    })
   */


  def canInsertAtPosition(insertAtPosition: NodeBasedTreePosition, newProgram: BeProgram): Boolean = {
    false
  }

  //lazy val asExpression: BeExpression = BeProgram.expressionTreeToExpression(expressionTree)

  override val toString: String = {
    "BeProgram(" + fullProgram.toString + ")"
  }

}

object BeProgram {

  def createSimpleFunc(displayConfig: BeTreeDisplayConfig, functionName: String, parNames: List[String], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram = {

    val funcNameMap: LanguageMap[HumanLanguage] = LanguageMap.universalMap(functionName)

    val parVariables: List[BeDefineVariable] = parNames.zip(parTypes).zipWithIndex.map((tup, curIndex) => {
      val (curName, curType) = tup
      BeDefineVariable(LanguageMap.universalMap(curName), curType)
    })

    val outputVar = output.map(typeSet => BeDefineVariable(LanguageMap.universalMap("output"), typeSet))

    val parValueMap = parVariables.zip(parValues).map((parVar, parVal) => {
      parVar -> BeUseValue(BeDataValueLiteral(parVal), Some(parVar))
    }).toMap

    val expression: BeExpression =
      BeFunctionCall(
        BeDefineFunction(
          parVariables, outputVar, BeExpression.pass, BeDefineFunction.functionInfo(funcNameMap)
        ),
        parValueMap
      )

    BeProgram(expression)
  }

  def createOneParFunc(displayConfig: BeTreeDisplayConfig, functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram = {
    createSimpleFunc(displayConfig, functionName, List(parName), List(parType), List(valueString), None)
  }

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
      BeDataType.Numeric)

    val forwardFunc = BeDefineFunction(
      List(parameter), None, BeExpression.pass, BeDefineFunction.functionInfo(forwardName)
    )

    BeStartProgram(
      BeSequence.optionalBody(
        List[BeExpression](
          BeFunctionCall(
            forwardFunc,
            Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))
          )
          ,
          BeFunctionCall(
            forwardFunc,
            Map(parameter -> BeUseValue(BeDataValueLiteral("10x0"), Some(parameter)))
          )
        )
      )
    )


  }

  def miniProgram(): BeProgram = {
    BeProgram(miniProgramExpression())
  }

  def sampleParsedProgram(): BeProgram = {
    val somePython =
      """
        |import turtle
        |callFunc("duck")
        |if score > 10:
        |    func(A)
        |else:
        |    forward(1000)
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def sampleParsedProgram2(): BeProgram = {
    val somePython =
      """
        |import os #import parsable but not supported yet
        |print("hello world")
        |if you´re happy and you know it syntax error #unparsable
        |# comment are supported
        |if score > 10:
        |    func(A)
        |elif score == 10:
        |    func("B")
        |else:
        |    forward(1000)
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }


}
