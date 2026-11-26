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

case class BeProgram(fullProgram: BeExpression) derives ReadWriter {
  override val toString: String = s"BeProgram($fullProgram)"
}

object BeProgram {
  /** Reserved source representation of Snap scripts that have no startup hat. */
  val unusedCodeName = BeEntityName.fromCodeString("unusedCode")
  def isUnusedCode(function: BeDefineFunction): Boolean =
    function.functionTypeInfo.displayName.universalInterpretation() == unusedCodeName.universalInterpretation()

  lazy val empty: BeProgram = BeProgram(BeStartProgram(BeExpression.pass))

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
          BeFunctionCall.fromDefinition(
            forwardFunc,
            Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))
          ),
          BeFunctionCall.fromDefinition(
            forwardFunc,
            Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))
          )
        )
      )
    )
  }

  def miniProgram(): BeProgram =
    BeProgram(miniProgramExpression())

  private def defineParameters(parNames: List[LanguageMap[HumanLanguage]], parTypes: List[BeDataType]): List[BeDefineVariable] =
    parNames.zip(parTypes).map((curName, curType) => BeDefineVariable(BeEntityName.fromMapInCodeNotation(curName), curType))

  private def createFunctionCallProgram(functionName: LanguageMap[HumanLanguage], parVariables: List[BeDefineVariable], parValues: List[String], output: Option[BeDataType]): BeProgram = {
    val outputVar = output.map(typeSet => BeDefineVariable(BeEntityName.fromCodeString("output"), typeSet))
    val parValueMap = parVariables.zip(parValues).map((parVar, parVal) => {
      parVar -> BeUseValue(BeDataValueLiteral(parVal), Some(parVar))
    }).toMap

    val expression: BeExpression =
      BeFunctionCall.fromDefinition(
        BeDefineFunction(
          parVariables, outputVar, BeExpression.pass, BeDefineFunction.functionInfo(BeEntityName.fromMapInCodeNotation(functionName))
        ),
        parValueMap
      )

    BeProgram(expression)
  }

}
