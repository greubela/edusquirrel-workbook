package plantworkshop

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.{BeIfElse, BeSequence}
import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import contentmanagement.model.vm.parsing.python.DefaultDefinitions
import contentmanagement.model.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeProgram

object PlantWorkshopTaskBlockLibraries {

  private def valueRefProgram(name: String, tpe: BeDataType): BeProgram = {
    val defVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](name), tpe)
    BeProgram(BeUseValue(BeUseValueReference(defVar), None))
  }

  private def stringLiteral(value: String): BeExpression = BeUseValue(BeDataValueLiteral(value), None)

  private def arduinoFuncCall(name: String, params: List[(String, BeDataType, BeExpression)]): BeExpression = {
    val inputDefs = params.map { case (pName, pType, _) =>
      BeDefineVariable(LanguageMap.universalMap[HumanLanguage](pName), pType)
    }

    val funcDef = BeDefineFunction(
      inputs = inputDefs,
      outputs = None,
      body = BeExpression.pass,
      functionTypeInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap[HumanLanguage](name))
    )

    val valueMap: Map[BeDefineVariable, BeExpression] = inputDefs.zip(params.map(_._3)).toMap
    BeFunctionCall(funcDef, valueMap)
  }

  private def arduinoFuncCallWithReturn(
      name: String,
      returnType: BeDataType,
      params: List[(String, BeDataType, BeExpression)]
  ): BeExpression = {
    val inputDefs = params.map { case (pName, pType, _) =>
      BeDefineVariable(LanguageMap.universalMap[HumanLanguage](pName), pType)
    }

    val outputVar = Some(BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("result"), returnType))

    val funcDef = BeDefineFunction(
      inputs = inputDefs,
      outputs = outputVar,
      body = BeExpression.pass,
      functionTypeInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap[HumanLanguage](name))
    )

    val valueMap: Map[BeDefineVariable, BeExpression] = inputDefs.zip(params.map(_._3)).toMap
    BeFunctionCall(funcDef, valueMap)
  }

  private def assignTyped(varName: String, varType: BeDataType, valueExpr: BeExpression): BeExpression = {
    val defVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](varName), varType)
    BeAssignVariable(defVar, valueExpr)
  }

  private def eqCondition(left: BeExpression, right: BeExpression): BeSequence = {
    val funcOpt = DefaultDefinitions.operatorDefinitionsBySymbolAndArity.get("==" -> 2)
    funcOpt match {
      case Some(funcDef) =>
        val leftVar = funcDef.inputs.head
        val rightVar = funcDef.inputs(1)
        val call = BeFunctionCall(funcDef, Map(leftVar -> left, rightVar -> right))
        BeSequence.conditionalBody(List(call))
      case None =>
        BeSequence.conditionalBody(List(BeUseValue(BeDataValueLiteral("true"), None)))
    }
  }

  private def serialPrintln(text: String): BeExpression = {
    arduinoFuncCall(
      "Serial.println",
      List(("text", BeDataType.String, stringLiteral(text)))
    )
  }

  private def delayMs(ms: String): BeExpression = {
    arduinoFuncCall(
      "delay",
      List(("ms", BeDataType.Int, BeUseValue(BeDataValueLiteral(ms), None)))
    )
  }

  private def digitalRead(pinName: String): BeExpression = {
    val pinVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](pinName), BeDataType.Int)
    arduinoFuncCallWithReturn(
      "digitalRead",
      BeDataType.Int,
      List(("pin", BeDataType.Int, BeUseValue(BeUseValueReference(pinVar), None)))
    )
  }

  private def digitalWrite(pinName: String, valueName: String): BeExpression = {
    val pinVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](pinName), BeDataType.Int)
    val valVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](valueName), BeDataType.Int)
    arduinoFuncCall(
      "digitalWrite",
      List(
        ("pin", BeDataType.Int, BeUseValue(BeUseValueReference(pinVar), None)),
        ("value", BeDataType.Int, BeUseValue(BeUseValueReference(valVar), None))
      )
    )
  }

  def task2LibraryPrograms(displayConfig: BeTreeDisplayConfig): List[BeProgram] = {
    val sensorValueVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("sensorValue"), BeDataType.Int)
    val highVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("HIGH"), BeDataType.Int)

    val readAssign = BeAssignVariable(sensorValueVar, digitalRead("MOISTURE_PIN"))

    val condition = eqCondition(
      BeUseValue(BeUseValueReference(sensorValueVar), None),
      BeUseValue(BeUseValueReference(highVar), None)
    )

    val thenBody = BeSequence.optionalBody(List(serialPrintln("Boden ist trocken.")))
    val elseBody = BeSequence.optionalBody(List(serialPrintln("Boden ist feucht.")))

    val ifElse = BeIfElse(condition, thenBody, elseBody)

    List(
      valueRefProgram("MOISTURE_PIN", BeDataType.Int),
      valueRefProgram("HIGH", BeDataType.Int),
      valueRefProgram("LOW", BeDataType.Int),
      BeProgram(readAssign),
      BeProgram(ifElse),
      BeProgram(serialPrintln("Text")),
      BeProgram(delayMs("2000"))
    )
  }

  def task3LibraryPrograms(displayConfig: BeTreeDisplayConfig): List[BeProgram] = {
    List(
      valueRefProgram("PUMP_PIN", BeDataType.Int),
      valueRefProgram("HIGH", BeDataType.Int),
      valueRefProgram("LOW", BeDataType.Int),
      BeProgram(digitalWrite("PUMP_PIN", "HIGH")),
      BeProgram(delayMs("1000")),
      BeProgram(digitalWrite("PUMP_PIN", "LOW")),
      BeProgram(delayMs("60000"))
    )
  }

  def task4LibraryPrograms(displayConfig: BeTreeDisplayConfig): List[BeProgram] = {
    val sensorValueVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("sensorValue"), BeDataType.Int)
    val highVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("HIGH"), BeDataType.Int)

    val readAssign = BeAssignVariable(sensorValueVar, digitalRead("MOISTURE_PIN"))
    val condition = eqCondition(
      BeUseValue(BeUseValueReference(sensorValueVar), None),
      BeUseValue(BeUseValueReference(highVar), None)
    )

    val thenBody = BeSequence.optionalBody(
      List(
        digitalWrite("PUMP_PIN", "HIGH"),
        delayMs("2000"),
        digitalWrite("PUMP_PIN", "LOW")
      )
    )

    // If soil is NOT dry, do nothing.
    val elseBody = BeSequence.optionalBody(Nil)
    val ifElse = BeIfElse(condition, thenBody, elseBody)

    List(
      valueRefProgram("MOISTURE_PIN", BeDataType.Int),
      valueRefProgram("PUMP_PIN", BeDataType.Int),
      valueRefProgram("HIGH", BeDataType.Int),
      valueRefProgram("LOW", BeDataType.Int),
      BeProgram(readAssign),
      BeProgram(ifElse),
      BeProgram(digitalWrite("PUMP_PIN", "HIGH")),
      BeProgram(digitalWrite("PUMP_PIN", "LOW")),
      BeProgram(delayMs("2000")),
      BeProgram(delayMs("10000"))
    )
  }

  def task2SuggestedStartProgram(): BeProgram = {
    val sensorValueVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("sensorValue"), BeDataType.Int)
    val highVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("HIGH"), BeDataType.Int)

    val readAssign = BeAssignVariable(sensorValueVar, digitalRead("MOISTURE_PIN"))
    val condition = eqCondition(
      BeUseValue(BeUseValueReference(sensorValueVar), None),
      BeUseValue(BeUseValueReference(highVar), None)
    )

    val thenBody = BeSequence.optionalBody(List(serialPrintln("Boden ist trocken.")))
    val elseBody = BeSequence.optionalBody(List(serialPrintln("Boden ist feucht.")))
    val ifElse = BeIfElse(condition, thenBody, elseBody)

    val seq = BeSequence.optionalBody(List(readAssign, ifElse, delayMs("2000")))
    BeProgram(BeStartProgram(seq))
  }

  def task3SuggestedStartProgram(): BeProgram = {
    val seq = BeSequence.optionalBody(
      List(
        digitalWrite("PUMP_PIN", "HIGH"),
        delayMs("1000"),
        digitalWrite("PUMP_PIN", "LOW"),
        delayMs("60000")
      )
    )

    BeProgram(BeStartProgram(seq))
  }

  // Optional: a small starter program skeleton that roughly matches the old advanced snippet.
  def task4SuggestedStartProgram(): BeProgram = {
    val code =
      """int sensorValue = digitalRead(MOISTURE_PIN);
        |if (sensorValue == HIGH) {
        |  digitalWrite(PUMP_PIN, HIGH);
        |  delay(2000);
        |  digitalWrite(PUMP_PIN, LOW);
        |} else {
        |}
        |delay(10000);
        |""".stripMargin

    val seq = contentmanagement.model.vm.parsing.cpp.CppParser.parseCpp(code)
    BeProgram(BeStartProgram(seq))
  }
}
