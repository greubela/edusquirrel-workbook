package it.evadid.vm.parsing.java.clean

sealed trait JavaAST

object JavaAST {
  case class JavaProgram(statements: Seq[StatementWithLineNumber]) extends JavaAST
  case class StatementWithLineNumber(statement: JavaStatement, lineNumber: Int) extends JavaAST
  case class JavaExecutionBlock(statements: Seq[JavaStatement]) extends JavaAST

  sealed trait JavaStatement extends JavaAST
  case class JavaUnparsableStatement(source: String) extends JavaStatement
  case object JavaEmptyStatement extends JavaStatement
  case class JavaImportStatement(name: String, isStatic: Boolean, importAll: Boolean) extends JavaStatement
  case class JavaPackageStatement(name: String) extends JavaStatement
  case class JavaReturnStatement(expression: Option[JavaExpression]) extends JavaStatement
  case class JavaThrowStatement(expression: JavaExpression) extends JavaStatement
  case object JavaBreakStatement extends JavaStatement
  case object JavaContinueStatement extends JavaStatement

  case class JavaIfStatement(condition: JavaExpression, thenBlock: JavaExecutionBlock, elseBlock: Option[JavaExecutionBlock]) extends JavaStatement
  case class JavaWhileStatement(condition: JavaExpression, bodyBlock: JavaExecutionBlock) extends JavaStatement
  case class JavaForStatement(init: Seq[JavaStatement], condition: Option[JavaExpression], update: Seq[JavaExpression], bodyBlock: JavaExecutionBlock) extends JavaStatement
  case class JavaTryStatement(body: JavaExecutionBlock, catches: Seq[JavaCatchClause], finallyBlock: Option[JavaExecutionBlock]) extends JavaStatement
  case class JavaCatchClause(parameter: JavaVariableDeclaration, body: JavaExecutionBlock) extends JavaAST

  case class JavaClassDef(name: String, modifiers: Seq[String], extendsType: Option[JavaType], implementsTypes: Seq[JavaType], body: JavaExecutionBlock) extends JavaStatement
  case class JavaMethodDef(name: String, modifiers: Seq[String], returnType: Option[JavaType], parameters: Seq[JavaVariableDeclaration], body: JavaExecutionBlock) extends JavaStatement
  case class JavaVariableDeclaration(name: String, javaType: JavaType, value: Option[JavaExpression], modifiers: Seq[String] = Seq.empty) extends JavaStatement
  case class JavaAssignment(target: JavaTarget, value: JavaExpression) extends JavaStatement
  case class JavaAugAssignment(target: JavaTarget, operator: String, value: JavaExpression) extends JavaStatement

  sealed trait JavaExpression extends JavaStatement
  case class JavaFunctionCall(name: JavaTarget, arguments: Seq[JavaExpression]) extends JavaExpression
  case class JavaNewExpression(javaType: JavaType, arguments: Seq[JavaExpression]) extends JavaExpression
  case class JavaAssignmentExpression(target: JavaTarget, operator: String, value: JavaExpression) extends JavaExpression
  case class JavaOperationBinary(left: JavaExpression, op: String, right: JavaExpression) extends JavaExpression
  case class JavaOperationUnary(op: String, operand: JavaExpression) extends JavaExpression

  sealed trait JavaAtomar extends JavaExpression
  case class JavaTarget(name: String, locationString: Seq[String] = Seq.empty, sliceExpr: Option[JavaExpression] = None) extends JavaAtomar
  case class JavaLiteral(literalAsString: String, javaType: JavaType) extends JavaAtomar
}
