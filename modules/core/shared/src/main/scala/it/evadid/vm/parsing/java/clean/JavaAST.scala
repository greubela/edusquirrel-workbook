package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.generic.abstractions.GenericAST.GenericAstLiteral

sealed trait JavaAST extends GenericAST

object JavaAST {

  /** A complete Java compilation-unit fragment, e.g. `package demo; class App {}`. */
  case class JavaProgram(statements: Seq[StatementWithLineNumber]) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  /** A parsed Java statement annotated with its source order, e.g. statement 0 for `package demo;`. */
  case class StatementWithLineNumber(statement: JavaStatement, lineNumber: Int) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = List(statement)
  }
  /** A brace-delimited statement block, e.g. `{ return 1; }`. */
  case class JavaExecutionBlock(statements: Seq[JavaStatement]) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  sealed trait JavaStatement extends JavaAST
  /** Unsupported Java syntax preserved as source text, e.g. an unimplemented switch expression. */
  case class JavaUnparsableStatement(source: String) extends JavaStatement
  /** An empty statement, e.g. `;`. */
  case object JavaEmptyStatement extends JavaStatement
  /** An import statement, e.g. `import java.util.List;` or `import static java.lang.Math.*;`. */
  case class JavaImportStatement(name: String, isStatic: Boolean, importAll: Boolean) extends JavaStatement
  /** A package declaration, e.g. `package demo.workbook;`. */
  case class JavaPackageStatement(name: String) extends JavaStatement
  /** A return statement, e.g. `return total;` or bare `return;`. */
  case class JavaReturnStatement(expression: Option[JavaExpression]) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = expression.toList
  }
  /** A throw statement, e.g. `throw ex;`. */
  case class JavaThrowStatement(expression: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(expression)
  }
  /** A loop exit statement, e.g. `break;`. */
  case object JavaBreakStatement extends JavaStatement
  /** A loop continuation statement, e.g. `continue;`. */
  case object JavaContinueStatement extends JavaStatement

  /** An if/else statement, e.g. `if (ready) { run(); } else { stop(); }`. */
  case class JavaIfStatement(
      condition: JavaExpression,
      thenBlock: JavaExecutionBlock,
      elseBlock: Option[JavaExecutionBlock]
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(condition, thenBlock) ++ elseBlock.toList
  }
  /** A while loop, e.g. `while (running) { tick(); }`. */
  case class JavaWhileStatement(condition: JavaExpression, bodyBlock: JavaExecutionBlock) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(condition, bodyBlock)
  }
  /** A for loop, e.g. `for (int i = 0; i < n; i++) { sum += i; }`. */
  case class JavaForStatement(
      init: Seq[JavaStatement],
      condition: Option[JavaExpression],
      update: Seq[JavaExpression],
      bodyBlock: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = init ++ condition.toList ++ update ++ Seq(bodyBlock)
  }
  /** A try/catch/finally statement, e.g. `try { run(); } catch (Exception ex) { throw ex; }`. */
  case class JavaTryStatement(
      body: JavaExecutionBlock,
      catches: Seq[JavaCatchClause],
      finallyBlock: Option[JavaExecutionBlock]
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(body) ++ catches ++ finallyBlock.toList
  }
  /** A catch clause, e.g. `catch (Exception ex) { handle(ex); }`. */
  case class JavaCatchClause(parameter: JavaVariableDeclaration, body: JavaExecutionBlock) extends JavaAST {
    override def getChildren(): Seq[GenericAST] = Seq(parameter, body)
  }

  /** A class definition, e.g. `public class App extends Base implements Runnable {}`. */
  case class JavaClassDef(
      name: String,
      modifiers: Seq[String],
      extendsType: Option[JavaType[?]],
      implementsTypes: Seq[JavaType[?]],
      body: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(body)
  }
  /** A method definition, e.g. `public int add(int a, int b) { return a + b; }`. */
  case class JavaMethodDef(
      name: String,
      modifiers: Seq[String],
      returnType: Option[JavaType[?]],
      parameters: Seq[JavaVariableDeclaration],
      body: JavaExecutionBlock
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = parameters ++ Seq(body)
  }
  /** A variable declaration, e.g. `private int count = 0;`. */
  case class JavaVariableDeclaration(
      name: String,
      javaType: JavaType[?],
      value: Option[JavaExpression],
      modifiers: Seq[String] = Seq.empty
  ) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = value.toList
  }
  /** A simple assignment, e.g. `count = 1;`. */
  case class JavaAssignment(target: JavaTarget, value: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }
  /** An augmented assignment, e.g. `count += 1;`. */
  case class JavaAugAssignment(target: JavaTarget, operator: String, value: JavaExpression) extends JavaStatement {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }

  sealed trait JavaExpression extends JavaStatement
  /** A compatibility node for simple target calls, e.g. `println(value)`. */
  case class JavaFunctionCall(name: JavaTarget, arguments: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(name) ++ arguments
  }
  /** A constructor call, e.g. `new ArrayList<String>()`. */
  case class JavaNewExpression(javaType: JavaType[?], arguments: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = arguments
  }
  /** An assignment used as an expression, e.g. `(count = next())`. */
  case class JavaAssignmentExpression(target: JavaTarget, operator: String, value: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(target, value)
  }
  /** A binary operation, e.g. `left + right` or `a && b`. */
  case class JavaOperationBinary(left: JavaExpression, op: String, right: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(left, right)
  }
  /** A unary operation, e.g. `!ready` or `-count`. */
  case class JavaOperationUnary(op: String, operand: JavaExpression) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(operand)
  }

  /** Attribute/member access on any expression, e.g. `object.field` or `factory().value`. */
  case class JavaAttributeAccess(receiver: JavaExpression, name: String) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = Seq(receiver)
  }
  /** Subscript/array access on any expression, e.g. `items[0]` or `matrix[i][j]`. */
  case class JavaSubscript(receiver: JavaExpression, indices: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = receiver +: indices
  }
  /** A call whose callee is any expression, e.g. `factory().create(x)` or `func()(x)`. */
  case class JavaCallExpression(callee: JavaExpression, arguments: Seq[JavaExpression]) extends JavaExpression {
    override def getChildren(): Seq[GenericAST] = callee +: arguments
  }

  sealed trait JavaAtomar extends JavaExpression
  /** An identifier-like target, e.g. `count`, `this.value`, or `items[0]` in assignments. */
  case class JavaTarget(name: String, locationString: Seq[String] = Seq.empty, sliceExpr: Option[JavaExpression] = None) extends JavaAtomar {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList
  }

  /** A typed Java literal, e.g. `123`, `"Ada"`, `true`, or `null`. */
  case class JavaLiteral[T](literalValue: String, literalType: JavaType[T]) extends JavaAtomar with GenericAstLiteral[T, JavaType[T], JavaLiteral[T]] {

  }
}
