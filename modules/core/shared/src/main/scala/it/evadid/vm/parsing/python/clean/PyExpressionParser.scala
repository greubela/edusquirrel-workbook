package it.evadid.vm.parsing.python.clean

import fastparse.*
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.generic.LiteralParser.literal
import it.evadid.vm.parsing.python.clean.PyAST.*
import it.evadid.vm.parsing.python.clean.Python313Parser.*
import fastparse.NoWhitespace._

object PyExpressionParser {


  // ==========================================
  // 4. Fully Layered Expressions Precedence Engine
  // ==========================================
  /*def expression[ctx: P]: P[PyAST] =


  def expression[ctx: P]: P[PyExpression] =
    //P(NAME ~ SPACES ~ COLONEQUAL ~ SPACES ~ expression).map { case (t, v) => NamedExpr(t, v) } |
    disjunction

  def named_expression[ctx: P]: P[PyAST] = P(

  )*/


  def binaryFromList(operator: String, first: PyExpression, other: Seq[PyExpression]): PyExpression = {
    if (other.isEmpty) first
    else if (other.size == 1) PyOperationBinary(first, operator, other.head)
    else PyOperationBinary(first, operator, binaryFromList(operator, other.head, other.tail))
  }

  def named_expression[ctx: P]: P[PyExpression] = P(target() ~~ SPACES.? ~~ COLONEQUAL ~~ SPACES.? ~~ expression)
    .map { case (name: PyTarget, expr: PyExpression) => NamedExpression(name.name, expr) }

  def expression[ctx: P]: P[PyExpression] = function_call | named_expression | disjunction

  def disjunction[ctx: P]: P[PyExpression] = conjunction |
    P(conjunction ~ SPACES ~ OR ~ SPACES ~ disjunction).map { case (left: PyExpression, right: PyExpression) => PyOperationBinary(left, "or", right) }

  def conjunction[ctx: P]: P[PyExpression] = inversion |
    P(inversion ~ SPACES ~ AND ~ SPACES ~ conjunction).map { case (left: PyExpression, right: PyExpression) => PyOperationBinary(left, "and", right) }

  def inversion[ctx: P]: P[PyExpression] = comparison |
    P(NOT ~ SPACES.? ~ inversion).map(PyOperationUnary("not", _))

  def comparison[ctx: P]: P[PyExpression] = bitwise_or |
    P(bitwise_or ~ SPACES.? ~ COMPAREOP ~ SPACES.? ~ comparison).map { case (left: PyExpression, operator: String, right: PyExpression) => PyOperationBinary(left, operator, right) }

  def bitwise_or[ctx: P]: P[PyExpression] = bitwise_xor |
    P(bitwise_xor ~ SPACES.? ~ VBAR ~ SPACES.? ~ bitwise_or).map { case (left: PyExpression, right: PyExpression) => PyOperationBinary(left, "|", right) }

  def bitwise_xor[ctx: P]: P[PyExpression] = bitwise_and |
    P(bitwise_and ~ SPACES.? ~ CIRCUMFLEX ~ SPACES.? ~ bitwise_xor).map { case (left: PyExpression, right: PyExpression) => PyOperationBinary(left, "^", right) }

  def bitwise_and[ctx: P]: P[PyExpression] = shift_expr |
    P(shift_expr ~ SPACES.? ~ AMPER ~ SPACES.? ~ bitwise_and).map { case (left: PyExpression, right: PyExpression) => PyOperationBinary(left, "&", right) }

  def shift_expr[ctx: P]: P[PyExpression] = sum |
    P(sum ~ SPACES.? ~ SHIFTOP ~ SPACES.? ~ shift_expr).map(PyOperationBinary(_, _, _))

  def sum[ctx: P]: P[PyExpression] = term
    | P(term ~ SPACES.? ~ PLUS ~ SPACES.? ~ sum).map(PyOperationBinary(_, "+", _))
    | P(term ~ SPACES.? ~ MINUS ~ SPACES.? ~ sum).map(PyOperationBinary(_, "-", _))

  def term[ctx: P]: P[PyExpression] = factor |
    P(factor ~ SPACES.? ~ MULTLIKEOP ~ SPACES.? ~ term).map(PyOperationBinary(_, _, _))

  def factor[ctx: P]: P[PyExpression] = power |
    P(UNARYPREFIX ~ SPACES.? ~ factor).map(PyOperationUnary(_, _))

  def power[ctx: P]: P[PyExpression] = primary |
    P(primary ~ SPACES.? ~ DOUBLESTAR ~ SPACES ~ factor).map(PyOperationBinary(_, "**", _))

  def primary[ctx: P]: P[PyExpression] = P(parenthesizedExpression | listLiteral | tupleLiteral | target() | literal)

  def arguments[ctx: P]: P[Seq[PyExpression]] = P(expression.rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?)))

  def function_call[ctx: P]: P[PyExpression] = P(target() ~~ SPACES.? ~~ LPAR ~~/ SPACES.? ~~ arguments.? ~~ SPACES.? ~~ RPAR)
    .map { case (func: PyTarget, args: Option[Seq[PyExpression]]) => PyFunctionCall(func, args.getOrElse(List()).toList) }

  def expressionList[ctx: P]: P[Seq[PyExpression]] =
    P(expression.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?)) ~~ (SPACES.? ~~ COMMA).?)

  def listLiteral[ctx: P]: P[PyListLiteral] =
    P(LSQB ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RSQB)
      .map(elements => PyListLiteral(elements.getOrElse(List()).toList))

  def tupleLiteral[ctx: P]: P[PyTupleLiteral] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ COMMA ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RPAR)
      .map { case (head, tail) => PyTupleLiteral((head +: tail.getOrElse(List())).toList) }

  def parenthesizedExpression[ctx: P]: P[PyExpression] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ RPAR)

}


