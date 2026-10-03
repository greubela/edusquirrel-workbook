package it.evadid.vm.parsing.java

import it.evadid.vm.BeProgram
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

/** Java frontend for the Be expression model.
  *
  * The Java AST is rendered to the Python subset already understood by the VM. This
  * intentionally reuses the mature Python-to-Be conversion instead of maintaining a
  * second set of symbol-resolution and expression-construction rules.
  */
object JavaToBeExpressionParser {
  def parseProgram(java: String): Either[Throwable, BeProgram] =
    JavaParser.parse(java).flatMap { ast =>
      scala.util.Try(BeProgram.fromPythonString(JavaToPython.render(ast))).toEither
    }

  def parse(java: String): Either[Throwable, it.evadid.vm.code.abstractions.BeExpression] =
    parseProgram(java).map(_.fullProgram)

  private object JavaToPython {
    def render(program: JavaProgram): String =
      statements(program.statements.map(_.statement), 0)

    private def statements(values: Seq[JavaStatement], depth: Int): String = {
      val rendered = values.flatMap(statement(_, depth)).filter(_.nonEmpty)
      if rendered.isEmpty then indent(depth) + "pass" else rendered.mkString("\n")
    }

    private def block(value: JavaExecutionBlock, depth: Int): String = statements(value.statements, depth)

    private def statement(value: JavaStatement, depth: Int): Seq[String] = {
      val pad = indent(depth)
      value match
        case JavaEmptyStatement | _: JavaImportStatement | _: JavaPackageStatement => Seq.empty
        case JavaUnparsableStatement(source) => Seq(pad + "# unsupported Java: " + source.replace('\n', ' '))
        case JavaReturnStatement(expression) => Seq(pad + "return" + expression.fold("")(v => " " + expr(v)))
        case JavaThrowStatement(expression) => Seq(pad + "raise " + expr(expression))
        case JavaBreakStatement => Seq(pad + "break")
        case JavaContinueStatement => Seq(pad + "continue")
        case JavaVariableDeclaration(name, _, value, _) => Seq(pad + name + " = " + value.fold("None")(expr))
        case JavaAssignment(target, value) => Seq(pad + expr(target) + " = " + expr(value))
        case JavaAugAssignment(target, operator, value) => Seq(pad + expr(target) + s" $operator " + expr(value))
        case JavaIfStatement(condition, thenBlock, elseBlock) =>
          Seq(pad + "if " + expr(condition) + ":\n" + block(thenBlock, depth + 1) +
            elseBlock.fold("")(b => "\n" + pad + "else:\n" + block(b, depth + 1)))
        case JavaWhileStatement(condition, body) =>
          Seq(pad + "while " + expr(condition) + ":\n" + block(body, depth + 1))
        case JavaForStatement(init, condition, update, body) =>
          val before = init.flatMap(statement(_, depth))
          val loopBody = body.statements ++ update
          before :+ (pad + "while " + condition.fold("True")(expr) + ":\n" + statements(loopBody, depth + 1))
        case JavaMethodDef(name, _, _, parameters, body) =>
          Seq(pad + s"def $name(" + parameters.map(_.name).mkString(", ") + "):\n" + block(body, depth + 1))
        case JavaClassDef(name, _, _, _, body) =>
          Seq(pad + s"class $name:\n" + block(body, depth + 1))
        case JavaTryStatement(body, catches, finallyBlock) =>
          val caught = catches.map(c => "\n" + pad + s"except Exception as ${c.parameter.name}:\n" + block(c.body, depth + 1)).mkString
          val finished = finallyBlock.fold("")(b => "\n" + pad + "finally:\n" + block(b, depth + 1))
          Seq(pad + "try:\n" + block(body, depth + 1) + caught + finished)
        case expression: JavaExpression => Seq(pad + expr(expression))
    }

    private def expr(value: JavaExpression): String = value match
      case JavaTarget(name, location, slice) =>
        (location :+ name).mkString(".") + slice.fold("")(v => "[" + expr(v) + "]")
      case JavaLiteral(raw, _) => raw match
        case "true" => "True"
        case "false" => "False"
        case "null" => "None"
        case other => other
      case JavaFunctionCall(name, arguments) => expr(name) + arguments.map(expr).mkString("(", ", ", ")")
      case JavaCallExpression(callee, arguments) => expr(callee) + arguments.map(expr).mkString("(", ", ", ")")
      case JavaNewExpression(javaType, arguments) => javaType.toString + arguments.map(expr).mkString("(", ", ", ")")
      case JavaAssignmentExpression(target, operator, value) => s"(${expr(target)} $operator ${expr(value)})"
      case JavaOperationBinary(left, operator, right) =>
        val pythonOp = Map("&&" -> "and", "||" -> "or").getOrElse(operator, operator)
        s"(${expr(left)} $pythonOp ${expr(right)})"
      case JavaOperationUnary("!", operand) => s"(not ${expr(operand)})"
      case JavaOperationUnary(operator, operand) => s"($operator${expr(operand)})"
      case JavaAttributeAccess(receiver, name) => s"${expr(receiver)}.$name"
      case JavaSubscript(receiver, indices) => expr(receiver) + indices.map(v => "[" + expr(v) + "]").mkString

    private def indent(depth: Int): String = "    " * depth
  }
}
