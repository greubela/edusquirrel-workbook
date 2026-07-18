package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.java.clean.JavaAST.*
import it.evadid.vm.parsing.java.clean.JavaLexer.*
import it.evadid.vm.parsing.java.clean.JavaType.*

object JavaExpressionParser {
  def expression[$: P]: P[JavaExpression] = P(assignmentExpression | logicalOr)
  def assignmentExpression[$: P]: P[JavaExpression] = P(target ~ SPACES.? ~ operator("+=", "-=", "*=", "/=", "%=", "=") ~ SPACES.? ~ expression).map(JavaAssignmentExpression(_, _, _))
  def logicalOr[$: P]: P[JavaExpression] = binary(logicalAnd, "||")
  def logicalAnd[$: P]: P[JavaExpression] = binary(equality, "&&")
  def equality[$: P]: P[JavaExpression] = binary(comparison, "==", "!=")
  def comparison[$: P]: P[JavaExpression] = binary(sum, "<=", ">=", "<", ">")
  def sum[$: P]: P[JavaExpression] = binary(term, "+", "-")
  def term[$: P]: P[JavaExpression] = binary(factor, "*", "/", "%")
  def factor[$: P]: P[JavaExpression] = P(operator("!", "-", "+") ~ SPACES.? ~ factor).map(JavaOperationUnary(_, _)) | primary

  private def binary[$: P](next: => P[JavaExpression], ops: String*): P[JavaExpression] =
    P(next ~ (SPACES.? ~ operator(ops*) ~ SPACES.? ~ next).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) => JavaOperationBinary(left, op, right) }
    }

  def primary[$: P]: P[JavaExpression] = P(newExpression | functionCall | literal | parenthesized | target)
  def parenthesized[$: P]: P[JavaExpression] = P(LPAR ~ SPACES.? ~ expression ~ SPACES.? ~ RPAR)
  def arguments[$: P]: P[Seq[JavaExpression]] = P(expression.rep(sep = SPACES.? ~ COMMA ~ SPACES.?))
  def functionCall[$: P]: P[JavaExpression] = P(target ~ SPACES.? ~ LPAR ~ SPACES.? ~ arguments.? ~ SPACES.? ~ RPAR).map {
    case (func, args) => JavaFunctionCall(func, args.getOrElse(Seq.empty))
  }
  def newExpression[$: P]: P[JavaExpression] = P(keyword("new") ~ SPACES ~ javaType ~ SPACES.? ~ LPAR ~ SPACES.? ~ arguments.? ~ SPACES.? ~ RPAR).map {
    case (typ, args) => JavaNewExpression(typ, args.getOrElse(Seq.empty))
  }
  def target[$: P]: P[JavaTarget] = P(qualifiedName ~ (SPACES.? ~ LSQB ~ SPACES.? ~ expression ~ SPACES.? ~ RSQB).?).map {
    case (name, slice) =>
      val parts = name.split('.').toSeq
      JavaTarget(parts.last, parts.dropRight(1), slice)
  }
  def literal[$: P]: P[JavaLiteral] = P(
    STRING_LITERAL.map(JavaLiteral(_, JAVA_STRING)) |
      NUMBER_LITERAL.map(raw => JavaLiteral(raw, if (raw.contains('.')) JAVA_DOUBLE else JAVA_INT)) |
      P(keyword("true") | keyword("false")).!.map(JavaLiteral(_, JAVA_BOOLEAN)) |
      P(keyword("null")).!.map(JavaLiteral(_, JAVA_CLASS("null")))
  )
}
