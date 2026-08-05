package it.evadid.vm.code.defining

import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.naming.{BeEntityName, KnownBeEntityNames}
import it.evadid.vm.types.BeDataType
import it.evadid.vm.types.BeDataType.{AnyType, Boolean as BooleanType, Int as IntType, Numeric as NumericType, String as StringType, Unit as UnitType}

/** Hard-coded, role-separated definition cache for known names. */
object KnownBeDefineStructures {
  private type FunctionSignature = (String, List[(String, BeDataType)], Option[BeDataType])
  private type OperatorSignature = (String, Int, List[(String, BeDataType)], Option[BeDataType])

  private val builtinFunctionSignatures: List[FunctionSignature] = List(
    ("str", List("value" -> AnyType), Some(StringType)),
    ("int", List("value" -> AnyType), Some(IntType)),
    ("float", List("value" -> AnyType), Some(NumericType)),
    ("bool", List("value" -> AnyType), Some(BooleanType)),
    ("abs", List("value" -> NumericType), Some(NumericType)),
    ("round", List("value" -> NumericType), Some(NumericType)),
    ("min", List("left" -> NumericType, "right" -> NumericType), Some(NumericType)),
    ("max", List("left" -> NumericType, "right" -> NumericType), Some(NumericType)),
    ("sum", List("values" -> AnyType), Some(NumericType)),
    ("len", List("value" -> AnyType), Some(IntType)),
    ("range", List("stop" -> IntType), Some(AnyType)),
    ("range", List("start" -> IntType, "stop" -> IntType), Some(AnyType)),
    ("range", List("start" -> IntType, "stop" -> IntType, "step" -> IntType), Some(AnyType)),
    ("print", List("value" -> AnyType), Some(UnitType)),
    ("input", List("prompt" -> StringType), Some(StringType)),
    ("sorted", List("values" -> AnyType), Some(AnyType)),
    ("enumerate", List("values" -> AnyType), Some(AnyType)),
    ("zip", List("left" -> AnyType, "right" -> AnyType), Some(AnyType)),
    ("type", List("value" -> AnyType), Some(AnyType)),
    ("isinstance", List("value" -> AnyType, "classInfo" -> AnyType), Some(BooleanType))
  )

  private val turtleMethodSignatures: List[FunctionSignature] = List(
    ("forward", List("distance" -> NumericType), Some(UnitType)),
    ("backward", List("distance" -> NumericType), Some(UnitType)),
    ("left", List("angle" -> NumericType), Some(UnitType)),
    ("right", List("angle" -> NumericType), Some(UnitType)),
    ("penUp", Nil, Some(UnitType)),
    ("penDown", Nil, Some(UnitType)),
    ("circle", List("radius" -> NumericType), Some(UnitType)),
    ("goto", List("x" -> NumericType, "y" -> NumericType), Some(UnitType)),
    ("setheading", List("angle" -> NumericType), Some(UnitType)),
    ("speed", List("value" -> NumericType), Some(UnitType)),
    ("color", List("value" -> StringType), Some(UnitType)),
    ("pensize", List("width" -> NumericType), Some(UnitType))
  )

  lazy val classes: List[BeDefineClass] = List(
    turtleClass,
    classDefinition("string"),
    classDefinition("integer"),
    classDefinition("number"),
    classDefinition("boolean"),
    classDefinition("list"),
    classDefinition("tuple"),
    classDefinition("dict"),
    classDefinition("set"),
    classDefinition("range"),
    classDefinition("object")
  )

  val functions: List[BeDefineFunction] =
    builtinFunctionSignatures.map((name, inputs, output) => function(name, inputs, output))

  private val operatorSignatures: List[OperatorSignature] = List(
    binary("+", NumericType, NumericType, NumericType),
    binary("+", IntType, IntType, IntType),
    binary("+", StringType, StringType, StringType),
    binary("+", StringType, AnyType, StringType),
    binary("+", AnyType, StringType, StringType),
    binary("-", NumericType, NumericType, NumericType),
    binary("-", IntType, IntType, IntType),
    unary("-", NumericType, NumericType),
    unary("-", IntType, IntType),
    unary("+", NumericType, NumericType),
    unary("+", IntType, IntType),
    binary("*", NumericType, NumericType, NumericType),
    binary("*", IntType, IntType, IntType),
    binary("*", StringType, IntType, StringType),
    binary("*", IntType, StringType, StringType),
    binary("/", NumericType, NumericType, NumericType),
    binary("//", NumericType, NumericType, NumericType),
    binary("//", IntType, IntType, IntType),
    binary("%", NumericType, NumericType, NumericType),
    binary("%", IntType, IntType, IntType),
    binary("**", NumericType, NumericType, NumericType),
    binary("@", AnyType, AnyType, AnyType),
    binary("<", StringType, StringType, BooleanType),
    binary("<=", StringType, StringType, BooleanType),
    binary(">", StringType, StringType, BooleanType),
    binary(">=", StringType, StringType, BooleanType),
    binary("<", NumericType, NumericType, BooleanType),
    binary("<=", NumericType, NumericType, BooleanType),
    binary(">", NumericType, NumericType, BooleanType),
    binary(">=", NumericType, NumericType, BooleanType),
    binary("==", AnyType, AnyType, BooleanType),
    binary("!=", AnyType, AnyType, BooleanType),
    binary("&", IntType, IntType, IntType),
    binary("|", IntType, IntType, IntType),
    binary("^", IntType, IntType, IntType),
    binary("<<", IntType, IntType, IntType),
    binary(">>", IntType, IntType, IntType),
    unary("~", IntType, IntType),
    binary("and", BooleanType, BooleanType, BooleanType),
    binary("or", BooleanType, BooleanType, BooleanType),
    unary("not", BooleanType, BooleanType),
    binary("is", AnyType, AnyType, BooleanType),
    binary("is not", AnyType, AnyType, BooleanType),
    binary("in", AnyType, AnyType, BooleanType),
    binary("not in", AnyType, AnyType, BooleanType)
  )

  val operators: List[BeDefineFunction] = operatorSignatures.map((symbol, position, inputs, output) =>
    BeDefineFunction(inputs.map((name, dataType) => variable(name, dataType)), output.map(dataType => variable("result", dataType)), BeSequence.optionalBody(List(BeExpression.pass)), BeDefineFunction.operatorInfo(symbol, position))
  )

  lazy val methods: List[BeDefineFunction] = classes.flatMap(_.methods)

  val variables: List[BeDefineVariable] = Nil

  lazy val allStructures: List[BeDefineStructure] = classes ++ functions ++ methods ++ operators ++ variables

  private lazy val byUniversalName: Map[String, List[BeDefineStructure]] = allStructures.groupBy(structureName)

  def byName(identifier: String): List[BeDefineStructure] =
    KnownBeEntityNames.byName(identifier).map(entity => byUniversalName.getOrElse(entity.universalInterpretation(), Nil)).getOrElse(Nil)

  private def classDefinition(name: String): BeDefineClass =
    BeDefineClass(BeEntityName.fromUniversalNameInParts(name), Nil, Nil)

  private lazy val turtleClass: BeDefineClass =
    BeDefineClass.withMethods(
      BeEntityName.fromUniversalNameInParts("turtle"),
      Nil,
      turtleMethodSignatures.map((name, inputs, output) =>
        BeDefineClass.MethodSignature(
          BeEntityName.fromUniversalNameInParts(name),
          inputs.map((paramName, dataType) => variable(paramName, dataType)),
          output.map(dataType => variable("result", dataType))
        )
      )
    )

  private def variable(name: String, dataType: BeDataType): BeDefineVariable =
    BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), dataType)

  private def function(name: String, inputs: List[(String, BeDataType)], output: Option[BeDataType]): BeDefineFunction =
    BeDefineFunction(
      inputs.map((paramName, dataType) => variable(paramName, dataType)),
      output.map(dataType => variable("result", dataType)),
      BeSequence.optionalBody(List(BeExpression.pass)),
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(name))
    )

  private def unary(symbol: String, input: BeDataType, output: BeDataType): OperatorSignature =
    (symbol, 0, List("value" -> input), Some(output))

  private def binary(symbol: String, left: BeDataType, right: BeDataType, output: BeDataType): OperatorSignature =
    (symbol, 1, List("left" -> left, "right" -> right), Some(output))

  private def structureName(structure: BeDefineStructure): String = structure match {
    case cls: BeDefineClass => cls.name.universalInterpretation()
    case fn: BeDefineFunction => fn.functionTypeInfo.displayName.universalInterpretation()
    case variable: BeDefineVariable => variable.name.universalInterpretation()
    case _ => ""
  }
}
