package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.generic.abstractions.pipeline.GenericAstScanner
import it.evadid.vm.parsing.java.clean.JavaAST.*
import it.evadid.vm.parsing.java.clean.JavaLexer.*
import it.evadid.vm.parsing.java.clean.JavaType.*

object JavaParser extends GenericAstScanner[JavaAST] {

  // ==========================================
  // 1. HELPER
  // ==========================================

  private def programFrom(stmts: Seq[JavaStatement]): JavaProgram =
    JavaProgram(stmts.zipWithIndex.map { case (stmt, idx) => StatementWithLineNumber(stmt, idx) })

  private def ws[$: P]: P[Unit] = P((WS | LINE_COMMENT | BLOCK_COMMENT).rep)
  private def ws1[$: P]: P[Unit] = P((WS | LINE_COMMENT | BLOCK_COMMENT).rep(1))

  // ==========================================
  // 2. PROGRAM
  // ==========================================

  override def parseASTFromProgramString(programString: String): Either[Throwable, JavaAST] = parse(programString)

  def parse(javaCode: String): Either[Throwable, JavaProgram] = {
    fastparse.parse(javaCode.trim, c => javaProgram(using c)) match {
      case Parsed.Success(program, _) => Right(program)
      case f: Parsed.Failure => Left(Exception(s"Java Parsing Error: ${f.trace().longAggregateMsg}"))
    }
  }

  def javaProgram[$: P]: P[JavaProgram] = P(ws ~ statement.rep ~ ws ~ End).map(programFrom)

  // ==========================================
  // 3. SEQUENCES OF STATEMENTS OR EXPRESSIONS
  // ==========================================

  def block[$: P]: P[JavaExecutionBlock] = P(LBRACE ~ ws ~ statement.rep ~ ws ~ RBRACE).map(JavaExecutionBlock(_))

  def arguments[$: P]: P[Seq[JavaExpression]] = P(expression.rep(sep = ws ~ COMMA ~ ws))

  def parameters[$: P]: P[Seq[JavaVariableDeclaration]] = P(parameter.rep(sep = ws ~ COMMA ~ ws))

  // ==========================================
  // 4. STATEMENTS TYPES
  // ==========================================

  def statement[$: P]: P[JavaStatement] = P(ws ~ (compoundStatement | simpleStatement | unparsable) ~ ws)

  def simpleStatement[$: P]: P[JavaStatement] = P(
    packageStatement | importStatement | returnStatement | throwStatement | breakStatement | continueStatement |
      variableDeclaration | assignment | expressionStatement | emptyStatement
  )

  def compoundStatement[$: P]: P[JavaStatement] = P(classDef | methodDef | ifStatement | whileStatement | forStatement | tryStatement)

  def emptyStatement[$: P]: P[JavaStatement] = P(SEMI).map(_ => JavaEmptyStatement)

  def expressionStatement[$: P]: P[JavaStatement] = P(expression ~ ws ~ SEMI)

  def unparsable[$: P]: P[JavaUnparsableStatement] = P((!SEMI ~ !RBRACE ~ AnyChar).rep(1).! ~ SEMI.?).map(JavaUnparsableStatement(_))

  // ==========================================
  // 5. INDIVIDUAL STATEMENTS
  // ==========================================

  def packageStatement[$: P]: P[JavaPackageStatement] =
    P(keyword("package") ~ ws1 ~ qualifiedName ~ ws ~ SEMI).map(JavaPackageStatement(_))

  def importStatement[$: P]: P[JavaImportStatement] =
    P(keyword("import") ~ ws1 ~ (keyword("static").!.map(_ => "static") ~ ws1).? ~ qualifiedName ~ (DOT ~ "*".!).? ~ ws ~ SEMI).map {
      case (isStatic, name, all) => JavaImportStatement(name, isStatic.nonEmpty, all.nonEmpty)
    }

  def returnStatement[$: P]: P[JavaReturnStatement] =
    P(keyword("return") ~ (ws1 ~ expression).? ~ ws ~ SEMI).map(JavaReturnStatement(_))

  def throwStatement[$: P]: P[JavaThrowStatement] =
    P(keyword("throw") ~ ws1 ~ expression ~ ws ~ SEMI).map(JavaThrowStatement(_))

  def breakStatement[$: P]: P[JavaStatement] = P(keyword("break") ~ ws ~ SEMI).map(_ => JavaBreakStatement)

  def continueStatement[$: P]: P[JavaStatement] = P(keyword("continue") ~ ws ~ SEMI).map(_ => JavaContinueStatement)

  def ifStatement[$: P]: P[JavaIfStatement] =
    P(keyword("if") ~ ws ~ LPAR ~ ws ~ expression ~ ws ~ RPAR ~ ws ~ block ~ (ws ~ keyword("else") ~ ws ~ (block | ifStatement.map(stmt => JavaExecutionBlock(Seq(stmt))))).?).map {
      case (cond, thenBlock, elseBlock) => JavaIfStatement(cond, thenBlock, elseBlock)
    }

  def whileStatement[$: P]: P[JavaWhileStatement] =
    P(keyword("while") ~ ws ~ LPAR ~ ws ~ expression ~ ws ~ RPAR ~ ws ~ block).map(JavaWhileStatement(_, _))

  def forStatement[$: P]: P[JavaForStatement] =
    P(keyword("for") ~ ws ~ LPAR ~ ws ~ forInit.? ~ ws ~ SEMI ~ ws ~ expression.? ~ ws ~ SEMI ~ ws ~ expression.rep(sep = ws ~ COMMA ~ ws) ~ ws ~ RPAR ~ ws ~ block).map {
      case (init, cond, update, body) => JavaForStatement(init.getOrElse(Seq.empty), cond, update, body)
    }

  def tryStatement[$: P]: P[JavaTryStatement] =
    P(keyword("try") ~ ws ~ block ~ catchClause.rep ~ (ws ~ keyword("finally") ~ ws ~ block).?).map(JavaTryStatement(_, _, _))

  def catchClause[$: P]: P[JavaCatchClause] =
    P(ws ~ keyword("catch") ~ ws ~ LPAR ~ ws ~ parameter ~ ws ~ RPAR ~ ws ~ block).map(JavaCatchClause(_, _))

  // Assignments and declarations

  def modifiers[$: P]: P[Seq[String]] = P((modifier ~ ws1).rep)

  def variableDeclaration[$: P]: P[JavaVariableDeclaration] =
    P(variableDeclarationNoSemi ~ ws ~ SEMI)

  def variableDeclarationNoSemi[$: P]: P[JavaVariableDeclaration] =
    P(modifiers ~ javaType ~ ws1 ~ NAME ~ (ws ~ ASSIGN ~ ws ~ expression).?).map { case (mods, typ, name, value) => JavaVariableDeclaration(name, typ, value, mods) }

  def parameter[$: P]: P[JavaVariableDeclaration] =
    P(modifiers ~ javaType ~ ws1 ~ NAME).map { case (mods, typ, name) => JavaVariableDeclaration(name, typ, None, mods) }

  def assignment[$: P]: P[JavaStatement] = P(assignmentNoSemi ~ ws ~ SEMI)

  def assignmentNoSemi[$: P]: P[JavaStatement] =
    P(target ~ ws ~ (operator("+=", "-=", "*=", "/=", "%=") | ASSIGN.!) ~ ws ~ expression).map {
      case (target, "=", value) => JavaAssignment(target, value)
      case (target, op, value) => JavaAugAssignment(target, op, value)
    }

  // Definitions

  def methodDef[$: P]: P[JavaMethodDef] =
    P(modifiers ~ javaType.? ~ ws ~ NAME ~ ws ~ LPAR ~ ws ~ parameters.? ~ ws ~ RPAR ~ ws ~ block).map {
      case (mods, returnType, name, params, body) => JavaMethodDef(name, mods, returnType, params.getOrElse(Seq.empty), body)
    }

  def classDef[$: P]: P[JavaClassDef] =
    P(modifiers ~ keyword("class") ~ ws1 ~ NAME ~ (ws1 ~ keyword("extends") ~ ws1 ~ javaType).? ~ (ws1 ~ keyword("implements") ~ ws1 ~ javaType.rep(1, sep = ws ~ COMMA ~ ws)).? ~ ws ~ block).map {
      case (mods, name, ext, impl, body) => JavaClassDef(name, mods, ext, impl.getOrElse(Seq.empty), body)
    }

  private def forInit[$: P]: P[Seq[JavaStatement]] =
    P(variableDeclarationNoSemi.map(Seq(_)) | assignmentNoSemi.rep(1, sep = ws ~ COMMA ~ ws))

  // ==========================================
  // 7. EXPRESSION TYPES
  // ==========================================

  def expression[$: P]: P[JavaExpression] = P(assignmentExpression | logicalOr)

  def assignmentExpression[$: P]: P[JavaExpression] =
    P(target ~ ws ~ operator("+=", "-=", "*=", "/=", "%=", "=") ~ ws ~ expression).map(JavaAssignmentExpression(_, _, _))

  // ==========================================
  // 8. INDIVIDUAL EXPRESSIONS
  // ==========================================

  def logicalOr[$: P]: P[JavaExpression] = binary(logicalAnd, "||")
  def logicalAnd[$: P]: P[JavaExpression] = binary(equality, "&&")
  def equality[$: P]: P[JavaExpression] = binary(comparison, "==", "!=")
  def comparison[$: P]: P[JavaExpression] = binary(sum, "<=", ">=", "<", ">")
  def sum[$: P]: P[JavaExpression] = binary(term, "+", "-")
  def term[$: P]: P[JavaExpression] = binary(factor, "*", "/", "%")
  def factor[$: P]: P[JavaExpression] = P(operator("!", "-", "+") ~ ws ~ factor).map(JavaOperationUnary(_, _)) | primary

  private def binary[$: P](next: => P[JavaExpression], ops: String*): P[JavaExpression] =
    P(next ~ (ws ~ operator(ops*) ~ ws ~ next).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) => JavaOperationBinary(left, op, right) }
    }

  // ==========================================
  // 8. ATOMAR SEQUENCES
  // ==========================================

  def primary[$: P]: P[JavaExpression] = P(newExpression | functionCall | literal | parenthesized | target)

  def parenthesized[$: P]: P[JavaExpression] = P(LPAR ~ ws ~ expression ~ ws ~ RPAR)

  def functionCall[$: P]: P[JavaExpression] = P(target ~ ws ~ LPAR ~ ws ~ arguments.? ~ ws ~ RPAR).map {
    case (func, args) => JavaFunctionCall(func, args.getOrElse(Seq.empty))
  }

  def newExpression[$: P]: P[JavaExpression] =
    P(keyword("new") ~ ws1 ~ javaType ~ ws ~ LPAR ~ ws ~ arguments.? ~ ws ~ RPAR).map { case (typ, args) => JavaNewExpression(typ, args.getOrElse(Seq.empty)) }

  def target[$: P]: P[JavaTarget] = P(qualifiedName ~ (ws ~ LSQB ~ ws ~ expression ~ ws ~ RSQB).?).map {
    case (name, slice) =>
      val parts = name.split('.').toSeq
      JavaTarget(parts.last, parts.dropRight(1), slice)
  }

  // ==========================================
  // 9. Value Literals
  // ==========================================

  def literal[$: P]: P[JavaLiteral[?]] = P(
    STRING_LITERAL.map(JAVA_STRING().createLiteralUnsafe) |
      NUMBER_LITERAL.map(raw => if (raw.contains('.')) JAVA_FLOAT().createLiteralUnsafe(raw) else JAVA_INTEGER().createLiteralUnsafe(raw)) |
      P(keyword("true").!.map(_ => "true") | keyword("false").!.map(_ => "false")).map(JAVA_BOOL().createLiteralUnsafe) |
      P(keyword("null").!).map(raw => JAVA_UNPARSABLE_TYPE(raw).createLiteralUnsafe(raw): JavaLiteral[?])
  )

  // ==========================================
  // 10. Type Literals
  // ==========================================

  def javaType[$: P]: P[JavaType[?]] = P(
    keyword("boolean").map(_ => JAVA_BOOL()) |
      keyword("byte").map(_ => JAVA_INTEGER()) |
      keyword("short").map(_ => JAVA_INTEGER()) |
      keyword("int").map(_ => JAVA_INTEGER()) |
      keyword("long").map(_ => JAVA_INTEGER()) |
      keyword("float").map(_ => JAVA_FLOAT()) |
      keyword("double").map(_ => JAVA_FLOAT()) |
      keyword("void").map(_ => JAVA_UNPARSABLE_TYPE("void")) |
      qualifiedName.map(JAVA_UNPARSABLE_TYPE(_))
  )
}
