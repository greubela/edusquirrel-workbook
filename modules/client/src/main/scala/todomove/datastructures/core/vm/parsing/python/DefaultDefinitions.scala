package todomove.datastructures.core.vm.parsing.python

import todomove.datastructures.core.vm.types.BeDataType.{AnyType, Boolean => BooleanType, Numeric => NumericType, String => StringType}
import it.evadid.core.datastructures.language.LanguageMap

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import todomove.datastructures.core.vm.types.BeDataType

object DefaultDefinitions {

  type NamedFunction = (String, BeDefineFunction)

  private def unaryOperatorDefinition(
      symbol: String,
      operandType: BeDataType,
      resultType: BeDataType
  ): NamedFunction = {
    val parameters =
      List(BeDefineVariable(LanguageMap.universalMap("value"), operandType))
    val output = Some(
      BeDefineVariable(LanguageMap.universalMap("result"), resultType)
    )
    val function = BeDefineFunction(parameters, output, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 0))
    symbol -> function
  }

  private def binaryOperatorDefinition(
      symbol: String,
      leftType: BeDataType,
      rightType: BeDataType,
      resultType: BeDataType
  ): NamedFunction = {
    val parameters = List(
      BeDefineVariable(LanguageMap.universalMap("left"), leftType),
      BeDefineVariable(LanguageMap.universalMap("right"), rightType)
    )
    val output = Some(
      BeDefineVariable(LanguageMap.universalMap("result"), resultType)
    )
    val function = BeDefineFunction(parameters, output, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 1))
    symbol -> function
  }

  private def builtinFunctionDefinition(
      name: String,
      parameters: List[(String, BeDataType)],
      returnType: Option[BeDataType]
  ): NamedFunction = {
    val inputs = parameters.map { case (paramName, dataType) =>
      BeDefineVariable(LanguageMap.universalMap(paramName), dataType)
    }
    val output = returnType.map(dataType =>
      BeDefineVariable(LanguageMap.universalMap("result"), dataType)
    )
    val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
    val function = BeDefineFunction(inputs, output, BeExpression.pass, functionInfo)
    name -> function
  }

  val operatorDefinitionsWithSymbols: List[NamedFunction] = {
    val arithmeticOperators = List(
      binaryOperatorDefinition("+", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("+", StringType, StringType, StringType),
      binaryOperatorDefinition("+", StringType, AnyType, StringType),
      binaryOperatorDefinition("+", AnyType, StringType, StringType),
      binaryOperatorDefinition("-", NumericType, NumericType, NumericType),
      unaryOperatorDefinition("-", NumericType, NumericType),
      unaryOperatorDefinition("+", NumericType, NumericType),
      binaryOperatorDefinition("*", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("*", StringType, NumericType, StringType),
      binaryOperatorDefinition("*", NumericType, StringType, StringType),
      binaryOperatorDefinition("/", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("//", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("%", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("**", NumericType, NumericType, NumericType)
    )

    // TODO: Add matrix multiplication (@) when a matrix/list data type is available.

    val comparisonOperators = List(
      binaryOperatorDefinition("<", StringType, StringType, BooleanType),
      binaryOperatorDefinition("<=", StringType, StringType, BooleanType),
      binaryOperatorDefinition(">", StringType, StringType, BooleanType),
      binaryOperatorDefinition(">=", StringType, StringType, BooleanType),
      binaryOperatorDefinition("<", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition("<=", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition(">", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition(">=", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition("==", AnyType, AnyType, BooleanType),
      binaryOperatorDefinition("!=", AnyType, AnyType, BooleanType)
    )

    val bitwiseOperators = List(
      binaryOperatorDefinition("&", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("|", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("^", NumericType, NumericType, NumericType),
      binaryOperatorDefinition("<<", NumericType, NumericType, NumericType),
      binaryOperatorDefinition(">>", NumericType, NumericType, NumericType),
      unaryOperatorDefinition("~", NumericType, NumericType)
    )

    val booleanOperators = List(
      binaryOperatorDefinition("and", BooleanType, BooleanType, BooleanType),
      binaryOperatorDefinition("or", BooleanType, BooleanType, BooleanType),
      unaryOperatorDefinition("not", BooleanType, BooleanType)
    )

    val identityOperators = List(
      binaryOperatorDefinition("is", AnyType, AnyType, BooleanType),
      binaryOperatorDefinition("is not", AnyType, AnyType, BooleanType)
    )

    // TODO: Add membership operators (in / not in) once collection data types such as lists become available.

    arithmeticOperators ++ comparisonOperators ++ bitwiseOperators ++ booleanOperators ++ identityOperators
  }

  val builtinFunctionDefinitions: List[NamedFunction] = List(
    builtinFunctionDefinition("str", List("value" -> AnyType), Some(StringType)),
    builtinFunctionDefinition("int", List("value" -> AnyType), Some(NumericType)),
    builtinFunctionDefinition("float", List("value" -> AnyType), Some(NumericType)),
    builtinFunctionDefinition("bool", List("value" -> AnyType), Some(BooleanType)),
    builtinFunctionDefinition("abs", List("value" -> NumericType), Some(NumericType)),
    builtinFunctionDefinition("print", List("value" -> AnyType), Some(BeDataType.Unit))
  )

  val operatorDefinitions: List[BeDefineFunction] = operatorDefinitionsWithSymbols.map(_._2)

  lazy val operatorDefinitionsBySymbolAndArity: Map[(String, Int), BeDefineFunction] =
    operatorDefinitionsWithSymbols.foldLeft(Map.empty[(String, Int), BeDefineFunction]) { case (acc, (symbol, function)) =>
      acc.updated(symbol -> function.inputs.length, function)
    }

  lazy val builtinFunctionsByName: Map[String, BeDefineFunction] =
    builtinFunctionDefinitions.foldLeft(Map.empty[String, BeDefineFunction]) { case (acc, (name, function)) =>
      acc.updated(name, function)
    }
}
