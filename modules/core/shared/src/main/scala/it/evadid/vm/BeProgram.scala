package it.evadid.vm

import upickle.default.ReadWriter

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.python.PythonParser

case class BeProgram(fullProgram: BeExpression) derives ReadWriter {
  override val toString: String = s"BeProgram($fullProgram)"
}

object BeProgram {
  lazy val empty: BeProgram = BeProgram(BeStartProgram(BeExpression.pass))

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
    val forwardName: BeEntityName = BeEntityName.fromMapInCodeNotation(Map(AppLanguage.German -> "vorwärts", AppLanguage.English -> "forward"))

    val distanceName: BeEntityName = BeEntityName.fromMapInCodeNotation(Map(AppLanguage.German -> "distanz", AppLanguage.English -> "distance"))

    val parameter: BeDefineVariable = BeDefineVariable(distanceName, BeDataType.Numeric)

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
    val parsingResult = PythonParser.default.parsePythonWithDetails(pythonString)
    BeStartProgram(parsingResult.codeExpression)
  }

  private def defineParameters(parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType]): List[BeDefineVariable] =
    parNames.zip(parTypes).map((curName, curType) => BeDefineVariable(BeEntityName.fromMapInCodeNotation(curName), curType))

  private def createFunctionCallProgram(functionName: LanguageMap[HumanLanguage], parVariables: List[BeDefineVariable], parValues: List[String], output: Option[BeDataType]): BeProgram = {
    val outputVar = output.map(typeSet => BeDefineVariable(BeEntityName.fromCodeString("output"), typeSet))
    val parValueMap = parVariables.zip(parValues).map((parVar, parVal) => {
      parVar -> BeUseValue(BeDataValueLiteral(parVal), Some(parVar))
    }).toMap

    val expression: BeExpression =
      BeFunctionCall(
        BeDefineFunction(
          parVariables, outputVar, BeExpression.pass, BeDefineFunction.functionInfo(BeEntityName.fromMapInCodeNotation(functionName))
        ),
        parValueMap
      )

    BeProgram(expression)
  }

}
