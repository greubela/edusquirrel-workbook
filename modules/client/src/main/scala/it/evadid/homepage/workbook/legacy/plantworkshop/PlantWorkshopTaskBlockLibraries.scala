package it.evadid.homepage.workbook.legacy.plantworkshop

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.{BeIfElse, BeSequence}
import todomove.datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import todomove.datastructures.core.vm.parsing.python.DefaultDefinitions
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}

/**
  * Block library builders for the Plant Workshop tasks.
  * Provides small program fragments used in the block editor palettes.
  */
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

    val valueMap: Map[BeDefineVariable, BeExpression] = inputDefs.zip(params.map(_._3)).map { case (inputDef, expr) =>
      val withContext = expr match {
        case use: BeUseValue if use.contextIfKnown.isEmpty => use.copy(contextIfKnown = Some(inputDef))
        case other => other
      }
      inputDef -> withContext
    }.toMap
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

    val valueMap: Map[BeDefineVariable, BeExpression] = inputDefs.zip(params.map(_._3)).map { case (inputDef, expr) =>
      val withContext = expr match {
        case use: BeUseValue if use.contextIfKnown.isEmpty => use.copy(contextIfKnown = Some(inputDef))
        case other => other
      }
      inputDef -> withContext
    }.toMap
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

  private def ltCondition(left: BeExpression, right: BeExpression): BeSequence = {
    val funcOpt = DefaultDefinitions.operatorDefinitionsBySymbolAndArity.get("<" -> 2)
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

  private def serialPrint(text: String): BeExpression = {
    arduinoFuncCall(
      "Serial.print",
      List(("text", BeDataType.String, stringLiteral(text)))
    )
  }

  private def serialPrintExpr(expr: BeExpression): BeExpression = {
    arduinoFuncCall(
      "Serial.print",
      List(("value", BeDataType.AnyType, expr))
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

  private def analogRead(pinName: String): BeExpression = {
    val pinVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage](pinName), BeDataType.Int)
    arduinoFuncCallWithReturn(
      "analogRead",
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

  /** Blocks for moisture sensor basics (task 2). */
  def task2LibraryPrograms(): List[BeProgram] = {
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

  /** Blocks for pump control (task 3). */
  def task3LibraryPrograms(): List[BeProgram] = {
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

  /** Blocks for combined logic (task 4). */
  def task4LibraryPrograms(): List[BeProgram] = {
    val messwertVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("messwert"), BeDataType.Int)
    val grenzeVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("feuchtigkeitsGrenze"), BeDataType.Int)

    val readAssign = BeAssignVariable(messwertVar, analogRead("SENSOR_PIN"))
    val condition = ltCondition(
      BeUseValue(BeUseValueReference(messwertVar), None),
      BeUseValue(BeUseValueReference(grenzeVar), None)
    )

    val thenBody = BeSequence.optionalBody(
      List(
        serialPrintln(" -> trocken, Relais an!\n"),
        serialPrintln(" Pumpe an.\n"),
        digitalWrite("PUMP_PIN", "LOW"),
        delayMs("2000"),
        serialPrintln(" Fertig gegossen.\n"),
        digitalWrite("PUMP_PIN", "HIGH")
      )
    )

    val ifElse = BeIfElse(condition, thenBody, BeSequence.optionalBody(Nil))

    List(
      valueRefProgram("SENSOR_PIN", BeDataType.Int),
      valueRefProgram("SENSOR_POWER_PIN", BeDataType.Int),
      valueRefProgram("PUMP_PIN", BeDataType.Int),
      valueRefProgram("feuchtigkeitsGrenze", BeDataType.Int),
      valueRefProgram("HIGH", BeDataType.Int),
      valueRefProgram("LOW", BeDataType.Int),
      BeProgram(digitalWrite("SENSOR_POWER_PIN", "HIGH")),
      BeProgram(delayMs("10")),
      BeProgram(readAssign),
      BeProgram(digitalWrite("SENSOR_POWER_PIN", "LOW")),
      BeProgram(serialPrint("Analoger Wert: ")),
      BeProgram(serialPrintExpr(BeUseValue(BeUseValueReference(messwertVar), None))),
      BeProgram(ifElse),
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
    val messwertVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("messwert"), BeDataType.Int)
    val grenzeVar = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("feuchtigkeitsGrenze"), BeDataType.Int)

    val readAssign = BeAssignVariable(messwertVar, analogRead("SENSOR_PIN"))
    val condition = ltCondition(
      BeUseValue(BeUseValueReference(messwertVar), None),
      BeUseValue(BeUseValueReference(grenzeVar), None)
    )

    val thenBody = BeSequence.optionalBody(
      List(
        serialPrintln(" -> trocken, Relais an!\n"),
        serialPrintln(" Pumpe an.\n"),
        digitalWrite("PUMP_PIN", "LOW"),
        delayMs("2000"),
        serialPrintln(" Fertig gegossen.\n"),
        digitalWrite("PUMP_PIN", "HIGH")
      )
    )

    val ifElse = BeIfElse(condition, thenBody, BeSequence.optionalBody(Nil))

    val seq = BeSequence.optionalBody(
      List(
        digitalWrite("SENSOR_POWER_PIN", "HIGH"),
        delayMs("10"),
        readAssign,
        digitalWrite("SENSOR_POWER_PIN", "LOW"),
        serialPrint("Analoger Wert: "),
        serialPrintExpr(BeUseValue(BeUseValueReference(messwertVar), None)),
        ifElse,
        delayMs("10000")
      )
    )

    BeProgram(BeStartProgram(seq))
  }
}
