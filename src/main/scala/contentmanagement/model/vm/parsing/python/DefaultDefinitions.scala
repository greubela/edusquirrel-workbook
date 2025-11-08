package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.types.BeDataType
import contentmanagement.model.vm.types.BeDataType.{AnyType, Boolean => BooleanType, Numeric => NumericType, String => StringType}

object DefaultDefinitions {

  private def unaryOperatorDefinition(
      symbol: String,
      operandType: BeDataType,
      resultType: BeDataType
  ): BeDefineFunction = {
    val parameters = List(BeDefineVariable(LanguageMap.universalMap("value"), operandType))
    val output = Some(BeDefineVariable(LanguageMap.universalMap("result"), resultType))
    BeDefineFunction(parameters, output, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 0))
  }

  private def binaryOperatorDefinition(
      symbol: String,
      leftType: BeDataType,
      rightType: BeDataType,
      resultType: BeDataType
  ): BeDefineFunction = {
    val parameters = List(
      BeDefineVariable(LanguageMap.universalMap("left"), leftType),
      BeDefineVariable(LanguageMap.universalMap("right"), rightType)
    )
    val output = Some(BeDefineVariable(LanguageMap.universalMap("result"), resultType))
    BeDefineFunction(parameters, output, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 1))
  }

  val operatorDefinitions: List[BeDefineFunction] = {
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
      binaryOperatorDefinition("<", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition("<=", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition(">", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition(">=", NumericType, NumericType, BooleanType),
      binaryOperatorDefinition("<", StringType, StringType, BooleanType),
      binaryOperatorDefinition("<=", StringType, StringType, BooleanType),
      binaryOperatorDefinition(">", StringType, StringType, BooleanType),
      binaryOperatorDefinition(">=", StringType, StringType, BooleanType),
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
}
