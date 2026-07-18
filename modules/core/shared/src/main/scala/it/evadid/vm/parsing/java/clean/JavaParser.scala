package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.java.clean.JavaAST.*
import it.evadid.vm.parsing.java.clean.JavaExpressionParser.*
import it.evadid.vm.parsing.java.clean.JavaLexer.*
import it.evadid.vm.parsing.java.clean.JavaType.*

object JavaParser {
  def parse(javaCode: String): Either[String, JavaProgram] =
    fastparse.parse(javaCode, c => javaProgram(using c)) match {
      case Parsed.Success(program, _) => Right(program)
      case f: Parsed.Failure => Left(s"Java Parsing Error: ${f.trace().longAggregateMsg}")
    }

  private def programFrom(stmts: Seq[JavaStatement]): JavaProgram =
    JavaProgram(stmts.zipWithIndex.map { case (stmt, idx) => StatementWithLineNumber(stmt, idx) })

  def javaProgram[$: P]: P[JavaProgram] = P(SPACES.? ~ statement.rep ~ SPACES.? ~ End).map(programFrom)
  def block[$: P]: P[JavaExecutionBlock] = P(LBRACE ~ SPACES.? ~ statement.rep ~ SPACES.? ~ RBRACE).map(JavaExecutionBlock(_))
  def statement[$: P]: P[JavaStatement] = P(SPACES.? ~ (compoundStatement | simpleStatement | unparsable) ~ SPACES.?)
  def simpleStatement[$: P]: P[JavaStatement] = P(packageStatement | importStatement | returnStatement | throwStatement | breakStatement | continueStatement | variableDeclaration | assignment | expressionStatement | emptyStatement)
  def compoundStatement[$: P]: P[JavaStatement] = P(classDef | methodDef | ifStatement | whileStatement | forStatement | tryStatement)

  def emptyStatement[$: P]: P[JavaStatement] = P(SEMI).map(_ => JavaEmptyStatement)
  def packageStatement[$: P]: P[JavaStatement] = P(keyword("package") ~ SPACES ~ qualifiedName ~ SPACES.? ~ SEMI).map(JavaPackageStatement(_))
  def importStatement[$: P]: P[JavaStatement] = P(keyword("import") ~ SPACES ~ keyword("static").!.? ~ SPACES.? ~ qualifiedName ~ (DOT ~ "*".!).? ~ SPACES.? ~ SEMI).map {
    case (isStatic, name, all) => JavaImportStatement(name, isStatic.nonEmpty, all.nonEmpty)
  }
  def returnStatement[$: P]: P[JavaStatement] = P(keyword("return") ~ (SPACES ~ expression).? ~ SPACES.? ~ SEMI).map(JavaReturnStatement(_))
  def throwStatement[$: P]: P[JavaStatement] = P(keyword("throw") ~ SPACES ~ expression ~ SPACES.? ~ SEMI).map(JavaThrowStatement(_))
  def breakStatement[$: P]: P[JavaStatement] = P(keyword("break") ~ SPACES.? ~ SEMI).map(_ => JavaBreakStatement)
  def continueStatement[$: P]: P[JavaStatement] = P(keyword("continue") ~ SPACES.? ~ SEMI).map(_ => JavaContinueStatement)

  def modifiers[$: P]: P[Seq[String]] = P((modifier ~ SPACES).rep)
  def variableDeclaration[$: P]: P[JavaVariableDeclaration] = P(modifiers ~ javaType ~ SPACES ~ NAME ~ (SPACES.? ~ ASSIGN ~ SPACES.? ~ expression).? ~ SPACES.? ~ SEMI).map {
    case (mods, typ, name, value) => JavaVariableDeclaration(name, typ, value, mods)
  }
  def parameter[$: P]: P[JavaVariableDeclaration] = P(modifiers ~ javaType ~ SPACES ~ NAME).map { case (mods, typ, name) => JavaVariableDeclaration(name, typ, None, mods) }
  def parameters[$: P]: P[Seq[JavaVariableDeclaration]] = P(parameter.rep(sep = SPACES.? ~ COMMA ~ SPACES.?))
  def assignment[$: P]: P[JavaStatement] = P(target ~ SPACES.? ~ (operator("+=", "-=", "*=", "/=", "%=") | "=".!) ~ SPACES.? ~ expression ~ SPACES.? ~ SEMI).map {
    case (target, "=", value) => JavaAssignment(target, value)
    case (target, op, value) => JavaAugAssignment(target, op, value)
  }
  def expressionStatement[$: P]: P[JavaStatement] = P(expression ~ SPACES.? ~ SEMI)

  def ifStatement[$: P]: P[JavaIfStatement] = P(keyword("if") ~ SPACES.? ~ LPAR ~ SPACES.? ~ expression ~ SPACES.? ~ RPAR ~ SPACES.? ~ block ~ (SPACES.? ~ keyword("else") ~ SPACES.? ~ (block | ifStatement.map(stmt => JavaExecutionBlock(Seq(stmt))))).?).map {
    case (cond, thenBlock, elseBlock) => JavaIfStatement(cond, thenBlock, elseBlock)
  }
  def whileStatement[$: P]: P[JavaWhileStatement] = P(keyword("while") ~ SPACES.? ~ LPAR ~ SPACES.? ~ expression ~ SPACES.? ~ RPAR ~ SPACES.? ~ block).map(JavaWhileStatement(_, _))
  def forStatement[$: P]: P[JavaForStatement] = P(keyword("for") ~ SPACES.? ~ LPAR ~ SPACES.? ~ forInit.? ~ SPACES.? ~ SEMI ~ SPACES.? ~ expression.? ~ SPACES.? ~ SEMI ~ SPACES.? ~ expression.rep(sep = SPACES.? ~ COMMA ~ SPACES.?) ~ SPACES.? ~ RPAR ~ SPACES.? ~ block).map {
    case (init, cond, update, body) => JavaForStatement(init.getOrElse(Seq.empty), cond, update, body)
  }
  private def forInit[$: P]: P[Seq[JavaStatement]] = P(variableDeclarationNoSemi.map(Seq(_)) | assignmentNoSemi.rep(1, sep = SPACES.? ~ COMMA ~ SPACES.?))
  private def variableDeclarationNoSemi[$: P]: P[JavaVariableDeclaration] = P(modifiers ~ javaType ~ SPACES ~ NAME ~ (SPACES.? ~ ASSIGN ~ SPACES.? ~ expression).?).map { case (mods, typ, name, value) => JavaVariableDeclaration(name, typ, value, mods) }
  private def assignmentNoSemi[$: P]: P[JavaStatement] = P(target ~ SPACES.? ~ (operator("+=", "-=", "*=", "/=", "%=") | "=".!) ~ SPACES.? ~ expression).map {
    case (target, "=", value) => JavaAssignment(target, value)
    case (target, op, value) => JavaAugAssignment(target, op, value)
  }

  def tryStatement[$: P]: P[JavaTryStatement] = P(keyword("try") ~ SPACES.? ~ block ~ catchClause.rep ~ (SPACES.? ~ keyword("finally") ~ SPACES.? ~ block).?).map {
    case (body, catches, finallyBlock) => JavaTryStatement(body, catches, finallyBlock)
  }
  def catchClause[$: P]: P[JavaCatchClause] = P(SPACES.? ~ keyword("catch") ~ SPACES.? ~ LPAR ~ SPACES.? ~ parameter ~ SPACES.? ~ RPAR ~ SPACES.? ~ block).map(JavaCatchClause(_, _))
  def methodDef[$: P]: P[JavaMethodDef] = P(modifiers ~ javaType.? ~ SPACES.? ~ NAME ~ SPACES.? ~ LPAR ~ SPACES.? ~ parameters.? ~ SPACES.? ~ RPAR ~ SPACES.? ~ block).map {
    case (mods, returnType, name, params, body) => JavaMethodDef(name, mods, returnType, params.getOrElse(Seq.empty), body)
  }
  def classDef[$: P]: P[JavaClassDef] = P(modifiers ~ keyword("class") ~ SPACES ~ NAME ~ (SPACES ~ keyword("extends") ~ SPACES ~ javaType).? ~ (SPACES ~ keyword("implements") ~ SPACES ~ javaType.rep(1, sep = SPACES.? ~ COMMA ~ SPACES.?)).? ~ SPACES.? ~ block).map {
    case (mods, name, ext, impl, body) => JavaClassDef(name, mods, ext, impl.getOrElse(Seq.empty), body)
  }
  def unparsable[$: P]: P[JavaUnparsableStatement] = P((!SEMI ~ !RBRACE ~ AnyChar).rep(1).! ~ SEMI.?).map(JavaUnparsableStatement(_))
}
