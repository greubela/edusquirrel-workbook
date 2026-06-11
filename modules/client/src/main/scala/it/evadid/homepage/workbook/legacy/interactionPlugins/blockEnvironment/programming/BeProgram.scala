package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import it.evadid.core.datastructures.tree.{Tree, TreeStructureAndExecutionContext, TreeStructureContext}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.block.BeBlockRendererFactory
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockPlaceholder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import todomove.datastructures.core.vm.code.usage.{BeFunctionCall, BeUseValue}
import todomove.datastructures.core.vm.parsing.python.PythonParser
import todomove.datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeDataValueLiteral, BeScope}
import todomove.datastructures.core.vm.types.BeChildRole.NoRole

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeExpressionTree = Tree[NodeBasedTreePosition, BeExpressionNode]

type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]
type BeExpressionContext = TreeStructureContext[NodeBasedTreePosition, BeExpressionNode]

type BeBlockRenderingTree = Tree[NodeBasedTreePosition, (BeExpressionNode, BeBlock)]
type BeBlockRenderingContext = TreeStructureAndExecutionContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock), NestedBlockRenderer]

case class BeProgram(fullProgram: BeExpression) {

  def expressionTree(displayConfig: BeTreeDisplayConfig): BeExpressionTree = fullProgram.recToTree(displayConfig.displayPlaceholders, BeChildPosition(NoRole, BeScope.GlobalScope()))

  def blockRenderingTree(displayConfig: BeTreeDisplayConfig): BeBlockRenderingTree = expressionTree(displayConfig).mapWithStructure(structure => {
    structure.curValue match {
      case BeExtensionPoint(isRequired, childPos, dataType) => {
        (structure.curValue, BeBlockPlaceholder(structure.curValue.asInstanceOf[BeExtensionPoint], structure.curPosition))
      }
      case BeExpressionReference(childPos, expression) => {
        (structure.curValue, BeBlockRendererFactory.blockFor(expression))
      }
    }
  })

  def withInsertions(displayConfig: BeTreeDisplayConfig, additionMap: Map[BeExtensionPoint, BeExpression]): BeProgram = {
    val exprTree = expressionTree(displayConfig)
    val res: Map[NodeBasedTreePosition, Option[BeExpression]] = {
      exprTree.applyWithChildResults[Option[BeExpression]]((structureContext, childrenResultMap) => {
        structureContext.curValue match {
          case name@BeExtensionPoint(isRequired, childPos, dataType) => {
            if (additionMap.contains(name)) Some(additionMap(name))
            else None
          }
          case BeExpressionReference(childPos, expression) => {
            val childrenResultList: List[(BeChildRole, BeExpression)] =
              childrenResultMap.toList.filter(_._2.nonEmpty).map(tup => (tup._1.childPosition.roleInParent, tup._2.get))
            Some(expression.withReplacedChildren(childrenResultList))
          }
        }
      })
    }
    BeProgram(res(exprTree.rootPosition.forChild(0)).get)
  }



  /*
    val res: Option[(BeChildPosition, BeExpression)] = structureContext.curValue match {
      case BeExtensionPoint(isRequired, childPos, dataType) => {
        val replaceWith = additionMap.get(structureContext.curValue.asInstanceOf[BeExtensionPoint])
        if (replaceWith.nonEmpty && dataType.canTakeValuesFrom(replaceWith.get.canEvaluateTo).possibleWithoutSyntaxErrors) Some((childPos, replaceWith.get))
        else None
      }
      case BeExpressionReference(childPos, expression) => {
       // childrenResults.toList().map(tup => tup)
        ???
      }
        val childrenResults2: List[(BeChildRole, BeExpression)] = structureContext.accessChildrenResults.flatten.map(tup => (tup._1.roleInParent, tup._2))
        Some((childPos, expression.withReplacedChildren(childrenResults)))
      }

        ???
    }
    )

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
    })*/
  /*
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

  def fromPythonString(pythonString: String): BeProgram = {
    val parsingResult = PythonParser.parsePythonWithDetails(pythonString)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def createSimpleFunc(functionName: LanguageMap[HumanLanguage], parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram = {

    val parVariables: List[BeDefineVariable] = parNames.zip(parTypes).zipWithIndex.map((tup, curIndex) => {
      val (curName, curType) = tup
      BeDefineVariable(curName, curType)
    })

    val outputVar = output.map(typeSet => BeDefineVariable(LanguageMap.universalMap("output"), typeSet))

    val parValueMap = parVariables.zip(parValues).map((parVar, parVal) => {
      parVar -> BeUseValue(BeDataValueLiteral(parVal), Some(parVar))
    }).toMap

    val expression: BeExpression =
      BeFunctionCall(
        BeDefineFunction(
          parVariables, outputVar, BeExpression.pass, BeDefineFunction.functionInfo(functionName)
        ),
        parValueMap
      )

    BeProgram(expression)
  }

  def createSimpleFunc(functionName: String, parNames: List[String], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram = {

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


  def createOneParFunc(functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram = {
    createSimpleFunc(functionName, List(parName), List(parType), List(valueString), None)
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
            Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))
          )
        )
      )
    )


  }

  def miniProgram(): BeProgram = {
    BeProgram(miniProgramExpression())
  }

  def debugGraphicsProgram2(): BeProgram = {
    val somePython =
      """
        |i = 1
        |while i < 3:
        |    i = i + 1
        |println("finished!")
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def parseSimpleWhile(): BeProgram = {
    val somePython =
      """
        |i: int = 3
        |while i < 5:
        |    i = i + 1
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def parseSimpleIf(): BeProgram = {
    val somePython =
      """
        |if c:
        |    x = "hi"
        |else:
        |    x = "bye"
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def debugGraphicsProgram3(): BeProgram = {
    val somePython =
      """
        |import turtle
        |# comments are supported
        |        if i == 2:
        |            turnLeft(90)
        |        else:
        |            backward("never!")
        |    i = i + 1
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def debugGraphicsProgram(): BeProgram = {
    val somePython =
      """
        |import turtle
        |# comments are supported
        |syntax error!!
        |i = 3
        |while i < 10:
        |    j = 2
        |    while j < 2:
        |        j = j + 1
        |        if i == 2:
        |            turnLeft(90)
        |        else:
        |            backward("never!")
        |    i = i + 1
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def sampleParsedProgram(): BeProgram = {
    val somePython =
      """
        |import turtle
        |callFunc("duck")
        |if score > 10:
        |    func(A)
        |else:
        |    doSomething()
        |    if another > 3:
        |        forward(1000)
        |    else:
        |        backward("never!")
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = BeStartProgram(parsingResult.codeExpression)
    BeProgram(expression)
  }

  def sampleParsedProgram2(): BeProgram = {
    val somePython =
      """
        |import os
        |x=3
        |if you´re happy and you know it syntax error
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
