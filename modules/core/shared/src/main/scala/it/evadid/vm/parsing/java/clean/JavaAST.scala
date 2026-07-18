package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.generic.abstractions.GenericAST.GenericAstLiteral
import it.evadid.vm.parsing.python.clean.PythonType

sealed trait JavaAST extends GenericAST

object JavaAST {

  case class JavaProgram(statements: Seq[StatementWithLineNumber]) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  case class StatementWithLineNumber(statement: JavaStatement, lineNumber: Int) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = List(statement)
  }
  case class JavaExecutionBlock(statements: Seq[JavaStatement]) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  sealed trait JavaStatement extends JavaAST
  case class JavaUnparsableStatement(source: String) extends JavaStatement
  case object JavaEmptyStatement extends JavaStatement
  case class JavaImportStatement(name: String, isStatic: Boolean, importAll: Boolean) extends JavaStatement
  case class JavaPackageStatement(name: String) extends JavaStatement
  case class JavaReturnStatement(expression: Option[JavaExpression]) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = expression.toList
  }
  case class JavaThrowStatement(expression: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(expression)
  }
  case object JavaBreakStatement extends JavaStatement
  case object JavaContinueStatement extends JavaStatement

  case class JavaIfStatement(
      condition: JavaExpression,
      thenBlock: JavaExecutionBlock,
      elseBlock: Option[JavaExecutionBlock]
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(condition, thenBlock) ++ elseBlock.toList
  }
  case class JavaWhileStatement(condition: JavaExpression, bodyBlock: JavaExecutionBlock) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(condition, bodyBlock)
  }
  case class JavaForStatement(
      init: Seq[JavaStatement],
      condition: Option[JavaExpression],
      update: Seq[JavaExpression],
      bodyBlock: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = init ++ condition.toList ++ update ++ Seq(bodyBlock)
  }
  case class JavaTryStatement(
      body: JavaExecutionBlock,
      catches: Seq[JavaCatchClause],
      finallyBlock: Option[JavaExecutionBlock]
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(body) ++ catches ++ finallyBlock.toList
  }
  case class JavaCatchClause(parameter: JavaVariableDeclaration, body: JavaExecutionBlock) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = Seq(parameter, body)
  }

  case class JavaClassDef(
      name: String,
      modifiers: Seq[String],
      extendsType: Option[JavaType[?]],
      implementsTypes: Seq[JavaType[?]],
      body: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(body)
  }
  case class JavaMethodDef(
      name: String,
      modifiers: Seq[String],
      returnType: Option[JavaType[?]],
      parameters: Seq[JavaVariableDeclaration],
      body: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = parameters ++ Seq(body)
  }
  case class JavaVariableDeclaration(
      name: String,
      javaType: JavaType[?],
      value: Option[JavaExpression],
      modifiers: Seq[String] = Seq.empty
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = value.toList
  }
  case class JavaAssignment(target: JavaTarget, value: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }
  case class JavaAugAssignment(target: JavaTarget, operator: String, value: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }

  sealed trait JavaExpression extends JavaStatement
  case class JavaFunctionCall(name: JavaTarget, arguments: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(name) ++ arguments
  }
  case class JavaNewExpression(javaType: JavaType[?], arguments: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = arguments
  }
  case class JavaAssignmentExpression(target: JavaTarget, operator: String, value: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }
  case class JavaOperationBinary(left: JavaExpression, op: String, right: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(left, right)
  }
  case class JavaOperationUnary(op: String, operand: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(operand)
  }

  sealed trait JavaAtomar extends JavaExpression
  case class JavaTarget(name: String, locationString: Seq[String] = Seq.empty, sliceExpr: Option[JavaExpression] = None) extends JavaAtomar {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList
  }

  case class JavaLiteral[T](literalValue: String, literalType: JavaType[T]) extends GenericAstLiteral[T, JavaType[T], JavaLiteral[T]] {

  }
}
