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
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.workbook.vm.code.others.BeStartProgram
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import it.evadid.workbook.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.workbook.vm.parsing.python.PythonParser
import it.evadid.workbook.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeDataValueLiteral, BeScope}
import it.evadid.workbook.vm.types.BeChildRole.NoRole

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]
type BeExpressionTree = Tree[NodeBasedTreePosition, BeExpressionNode]

type BeBlockContext = TreeStructureContext[NodeBasedTreePosition, BeBlock]
type BeExpressionContext = TreeStructureContext[NodeBasedTreePosition, BeExpressionNode]

type BeBlockRenderingTree = Tree[NodeBasedTreePosition, (BeExpressionNode, BeBlock)]
type BeBlockRenderingContext = TreeStructureAndExecutionContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock), NestedBlockRenderer]

case class BeProgram(fullProgram: BeExpression) {

  def expressionTree(displayConfig: BeTreeDisplayConfig): BeExpressionTree =
    BeProgramViewHelpers.expressionTree(fullProgram, displayConfig)

  def blockRenderingTree(displayConfig: BeTreeDisplayConfig): BeBlockRenderingTree =
    BeProgramViewHelpers.blockRenderingTree(expressionTree(displayConfig))

  def withInsertions(displayConfig: BeTreeDisplayConfig, additionMap: Map[BeExtensionPoint, BeExpression]): BeProgram =
    BeProgramTreeMutationHelpers.withInsertions(this, displayConfig, additionMap)

  def canInsertAtPosition(insertAtPosition: NodeBasedTreePosition, newProgram: BeProgram): Boolean =
    BeProgramTreeMutationHelpers.canInsertAtPosition(this, insertAtPosition, newProgram)

  override val toString: String = BeProgramViewHelpers.toDisplayString(fullProgram)

}

object BeProgram {

  def fromPythonString(pythonString: String): BeProgram =
    BeProgramConstructionHelpers.fromPythonString(pythonString)

  def createSimpleFunc(functionName: LanguageMap[HumanLanguage], parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram =
    BeProgramConstructionHelpers.createSimpleFunc(functionName, parNames, parTypes, parValues, output)

  def createSimpleFunc(functionName: String, parNames: List[String], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram =
    BeProgramConstructionHelpers.createSimpleFunc(functionName, parNames, parTypes, parValues, output)

  def createOneParFunc(functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram =
    BeProgramConstructionHelpers.createOneParFunc(functionName, parName, parType, valueString)

  def miniProgramExpression(): BeExpression =
    BeProgramConstructionHelpers.miniProgramExpression()

  def miniProgram(): BeProgram =
    BeProgramConstructionHelpers.miniProgram()

  def debugGraphicsProgram2(): BeProgram =
    BeProgramConstructionHelpers.debugGraphicsProgram2()

  def parseSimpleWhile(): BeProgram =
    BeProgramConstructionHelpers.parseSimpleWhile()

  def parseSimpleIf(): BeProgram =
    BeProgramConstructionHelpers.parseSimpleIf()

  def debugGraphicsProgram3(): BeProgram =
    BeProgramConstructionHelpers.debugGraphicsProgram3()

  def debugGraphicsProgram(): BeProgram =
    BeProgramConstructionHelpers.debugGraphicsProgram()

  def sampleParsedProgram(): BeProgram =
    BeProgramConstructionHelpers.sampleParsedProgram()

  def sampleParsedProgram2(): BeProgram =
    BeProgramConstructionHelpers.sampleParsedProgram2()

}

private object BeProgramViewHelpers {

  def expressionTree(fullProgram: BeExpression, displayConfig: BeTreeDisplayConfig): BeExpressionTree =
    fullProgram.recToTree(displayConfig.displayPlaceholders, BeChildPosition(NoRole, BeScope.GlobalScope()))

  def blockRenderingTree(expressionTree: BeExpressionTree): BeBlockRenderingTree =
    expressionTree.mapWithStructure(structure => {
      structure.curValue match {
        case extensionPoint: BeExtensionPoint =>
          (structure.curValue, BeBlockPlaceholder(extensionPoint, structure.curPosition))
        case BeExpressionReference(childPos, expression) =>
          (structure.curValue, BeBlockRendererFactory.blockFor(expression))
      }
    })

  def toDisplayString(fullProgram: BeExpression): String =
    "BeProgram(" + fullProgram.toString + ")"

}

private object BeProgramTreeMutationHelpers {

  def withInsertions(program: BeProgram, displayConfig: BeTreeDisplayConfig, additionMap: Map[BeExtensionPoint, BeExpression]): BeProgram = {
    val exprTree = program.expressionTree(displayConfig)
    val res: Map[NodeBasedTreePosition, Option[BeExpression]] = {
      exprTree.applyWithChildResults[Option[BeExpression]]((structureContext, childrenResultMap) => {
        structureContext.curValue match {
          case name: BeExtensionPoint =>
            additionMap.get(name)
          case BeExpressionReference(childPos, expression) =>
            val childrenResultList: List[(BeChildRole, BeExpression)] =
              childrenResultMap.toList.filter(_._2.nonEmpty).map(tup => (tup._1.childPosition.roleInParent, tup._2.get))
            Some(expression.withReplacedChildren(childrenResultList))
        }
      })
    }
    BeProgram(res(exprTree.rootPosition.forChild(0)).get)
  }

  def canInsertAtPosition(program: BeProgram, insertAtPosition: NodeBasedTreePosition, newProgram: BeProgram): Boolean =
    false

}

private object BeProgramConstructionHelpers {

  def fromPythonString(pythonString: String): BeProgram =
    BeProgram(parsePythonExpression(pythonString))

  def createSimpleFunc(functionName: LanguageMap[HumanLanguage], parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram = {
    val parVariables = defineParameters(parNames, parTypes)
    createFunctionCallProgram(functionName, parVariables, parValues, output)
  }

  def createSimpleFunc(functionName: String, parNames: List[String], parTypes: List[BeDataType], parValues: List[String], output: Option[BeDataType]): BeProgram = {
    val funcNameMap: LanguageMap[HumanLanguage] = LanguageMap.universalMap(functionName)
    val parNameMaps: List[LanguageMap[HumanLanguage]] = parNames.map(LanguageMap.universalMap)
    createSimpleFunc(funcNameMap, parNameMaps, parTypes, parValues, output)
  }

  def createOneParFunc(functionName: String, parName: String, parType: BeDataType, valueString: String): BeProgram =
    createSimpleFunc(functionName, List(parName), List(parType), List(valueString), None)

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
          ),
          BeFunctionCall(
            forwardFunc,
            Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))
          )
        )
      )
    )
  }

  def miniProgram(): BeProgram =
    BeProgram(miniProgramExpression())

  def debugGraphicsProgram2(): BeProgram = fromPythonString(
    """
      |i = 1
      |while i < 3:
      |    i = i + 1
      |println("finished!")
      |""".stripMargin
  )

  def parseSimpleWhile(): BeProgram = fromPythonString(
    """
      |i: int = 3
      |while i < 5:
      |    i = i + 1
      |""".stripMargin
  )

  def parseSimpleIf(): BeProgram = fromPythonString(
    """
      |if c:
      |    x = "hi"
      |else:
      |    x = "bye"
      |""".stripMargin
  )

  def debugGraphicsProgram3(): BeProgram = fromPythonString(
    """
      |import turtle
      |# comments are supported
      |        if i == 2:
      |            turnLeft(90)
      |        else:
      |            backward("never!")
      |    i = i + 1
      |""".stripMargin
  )

  def debugGraphicsProgram(): BeProgram = fromPythonString(
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
  )

  def sampleParsedProgram(): BeProgram = fromPythonString(
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
  )

  def sampleParsedProgram2(): BeProgram = fromPythonString(
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
  )

  private def parsePythonExpression(pythonString: String): BeExpression = {
    val parsingResult = PythonParser.parsePythonWithDetails(pythonString)
    BeStartProgram(parsingResult.codeExpression)
  }

  private def defineParameters(parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType]): List[BeDefineVariable] =
    parNames.zip(parTypes).map((curName, curType) => BeDefineVariable(curName, curType))

  private def createFunctionCallProgram(functionName: LanguageMap[HumanLanguage], parVariables: List[BeDefineVariable], parValues: List[String], output: Option[BeDataType]): BeProgram = {
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

}
