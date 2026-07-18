package it.evadid.vm.parsing.python.clean

import fastparse.*
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.python.clean.PyAST.*
import it.evadid.vm.parsing.python.clean.PyExpressionParser.*
import it.evadid.vm.parsing.python.clean.PythonType.expression_type
import fastparse.NoWhitespace._

object Python313Parser {

  // ==========================================
  // 1. HELPER
  // ==========================================
  def programFrom(stmts: Seq[Seq[PyStatement]]): PyProgram = {
    val tups = stmts.zipWithIndex.flatMap((lineStmts: Seq[PyStatement], lineNr: Int) => lineStmts.map((lineNr, _)))
    PyProgram(tups.map((lineNr, stmt) => StatementWithLineNumber(stmt, lineNr)))
  }

  private val incIndent: Int = 4

  def checkIndent[ctx: P](state: IndentState): P[String] = P(" ".rep(state.currentLevel).!)

  def nextExecutionBlock[ctx: P](state: IndentState): P[PyExecutionBlock] = executionBlock(state.currentLevel + incIndent)

  // ==========================================
  // 2. PROGRAM AND STATEMENTS
  // ==========================================

  def parse(pythonCode: String): Either[String, PyProgram] = {
    fastparse.parse(pythonCode.trim + "\n", c => py_program(using c)) match {
      case Parsed.Success(astModule, _) => Right(astModule)
      case f: Parsed.Failure => Left(s"Python Parsing Error: ${f.trace().longAggregateMsg}")
    }
  }


  def py_program[ctx: P]: P[PyProgram] = P(
    (SPACES.? ~ NEWLINE.? ~ statement(IndentState(0)).rep ~~ NEWLINE.?).map(programFrom)
  )

  def emptyLine[ctx: P]: P[PyStatement] = P(SPACES.? ~~ NEWLINE).map(_ => PyEmptyStatement)

  def indentedStatement[ctx: P](innerState: IndentState): P[Seq[PyStatement]] = {
    P(checkIndent(innerState).! ~~ statement(innerState) ~~ SPACES.? ~~ NEWLINE).map { case (indent: String, stmt: Seq[PyStatement]) => stmt }
      | P(checkIndent(innerState).! ~~ PASS ~~ SPACES.? ~~ NEWLINE).map(_ => List())
      | emptyLine.map(Seq(_))
  }

  def executionBlock[ctx: P](indentNeeded: Int): P[PyExecutionBlock] = {
    P(indentedStatement(IndentState(indentNeeded)).rep(1).map((res: Seq[Seq[PyStatement]]) => PyExecutionBlock(res.flatten)))
  }

  def statement[ctx: P](state: IndentState): P[Seq[PyStatement]] = P(
    (simple_stmt(state).rep(1, sep = P(SPACES ~~ SEMI ~~ SPACES)) ~~ (SPACES ~~ SEMI).? ~~ NEWLINE).map((stmts: Seq[PyStatement]) => stmts)
      | compound_stmt(state).map((stmt: PyStatement) => Seq(stmt))
      | emptyLine.map(stmt => Seq(stmt))
      | P(ANYLINE ~~ NEWLINE).map(str => List(PyUnparsableStatement(str)))

  )

  def compound_stmt[ctx: P](state: IndentState): P[PyStatement] = {
    ifStmt(state) | whileStmt(state) | forStmt(state) | tryStmt(state) | class_def(state) | function_def(state)
  }
  /*function_def(state, Seq.empty) |
    class_def(state, Seq.empty) |
    withStmt(state) |
    asyncWithStmt(state) |
    matchStmt(state) |
    type_alias.map(ExprStmt(_)) |
*/


  def simple_stmt[ctx: P](state: IndentState): P[PyStatement] = P(
    import_stmt | assignment | emptyLine
      | P(PASS).map(_ => PyPassStatement)
      | P(BREAK).map(_ => PyBreakStatement)
      | P(CONTINUE).map(_ => PyContinueStatement)
      | P(RETURN ~ (expression).?).map { expr => PyReturnStatement(expr) }
      | P(RAISE ~ (expression).? ~ (FROM ~ expression).?).map { case (e, f) => PyRaiseStatement(e, f) }
      | expression
    /*type_alias |  del_stmt |    assert_stmt |  break_stmt | continue_stmt | expression.map(ExprStmt(_))*/
  )

  def targets_or_star[ctx: P]: P[Either[Seq[PyTarget], Boolean]] = {
    P(STAR.map(_ => Right(true)) | targetList.map(Left(_)))
  }

  def import_stmt[ctx: P]: P[PyStatement] = {
    P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES.? ~~ STAR).map { case (name: String) => PyImportFromStatement(name, List(), true) }
      | P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES ~~ targetList).map { case (name, list) => PyImportFromStatement(name, list.toList, false) }
      | P(IMPORT ~~ SPACES ~~ NAME).map { case (moduleName) => PyImportStatement(moduleName) }
  }



  // ==========================================
  // 3. INDIVIDUAL STATEMENTS
  // ==========================================

  // IF / ELIF / ELSE
  def ifStmt[ctx: P](state: IndentState): P[PyIfStatement] = P(
    IF ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ P(elifBranches(state) | elseBranch(state)).?
  ).map { case (expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyIfStatement(expr, body, orelse.getOrElse(passBlock)) }

  def elifBranches[ctx: P](state: IndentState): P[PyExecutionBlock] = P(
    checkIndent(state) ~~ ELIF ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ elifBranches(state).?)
    .map { case (indent: String, expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyExecutionBlock(List(PyIfStatement(expr, body, orelse.getOrElse(passBlock)))) }

  def elseBranch[ctx: P](state: IndentState): P[PyExecutionBlock] = P(
    checkIndent(state) ~~ ELSE ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state))
    .map { case (indent: String, elseBlock: PyExecutionBlock) => elseBlock }

  // WHILE / FOR / ASYNC FOR
  def whileStmt[ctx: P](state: IndentState): P[PyWhileStatement] = P(
    WHILE ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ elseBranch(state).?
  ).map { case (expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyWhileStatement(expr, body, orelse) }

  def forStmt[ctx: P](state: IndentState, isAsync: Boolean = false): P[PyForStatement] = P(
    (ASYNC ~~ SPACES.?).? ~~ FOR ~~ SPACES ~~ expression ~~ SPACES ~~ IN ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ elseBranch(state).?
  ).map { case (async: Option[String], expr1: PyExpression, expr2: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyForStatement(expr1, expr2, body, orelse, async.nonEmpty) }

  // try

  def tryStmt[ctx: P](state: IndentState): P[PyTryStatement] = P(
    TRY ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ exceptHandler(state).rep ~~ NEWLINE.? ~~ elseBranch(state).? ~~ finallyBlock(state).?
  ).map { case (body: PyExecutionBlock, handlers: Seq[PyExceptClause], orelse: Option[PyExecutionBlock], finalBody: Option[PyExceptClauseFinally]) => PyTryStatement(body, (handlers ++ finalBody).toList, orelse) }

  def finallyBlock[ctx: P](state: IndentState): P[PyExceptClauseFinally] = P(
    checkIndent(state) ~~ FINALLY ~~ SPACES ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state)
  ).map { case (indent: String, block: PyExecutionBlock) => PyExceptClauseFinally(block) }

  def exceptHandler[ctx: P](state: IndentState): P[PyExceptClause] =
    P(checkIndent(state) ~~ EXCEPT ~~ SPACES ~~ STAR ~~ SPACES ~~ expression ~~ (AS ~~ NAME).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state))
      .map { case (indent: String, expr: PyExpression, name: Option[String], body: PyExecutionBlock) => PyExceptClauseStar(expr, name, body) }
      |
      P(checkIndent(state) ~~ EXCEPT ~~ SPACES ~~ expression.? ~~ (SPACES ~~ AS ~~ NAME).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state))
        .map { case (indent, expression: Option[PyExpression], name: Option[String], body: PyExecutionBlock) => PyExceptClauseBasic(expression, name, body)
        }

  // DEFINITIONS

  def assignment[ctx: P]: P[PyAssignment] = P(
    P(target() ~~ SPACES.? ~~ (ASSIGN ~~ SPACES.? ~~ expression).?).map { case (target: PyTarget, expr: Option[PyExpression]) => PyAssignment(target, expr) }
    // | P(targetList ~~ (SPACES.? ~~ ASSIGN ~~ SPACES.? ~~ expression).?).map { case (targets: Seq[PyTarget], expr: Option[PyExpression]) => ??? }
  )

  def targetList[ctx: P]: P[Seq[PyTarget]] = P((target(List()).rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?))) | LPAR ~ targetList ~ RPAR)

  def identifier[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    P(NAME ~~ SPACES.? ~~ DOT ~~ SPACES.? ~~ identifier(knownContext)).map { case (name: String, target: PyTarget) => PyTarget(target.name, List(name) ++ target.locationString, target.sliceExpr, target.typeHint) }
      | NAME.map(PyTarget(_))
  }

  def identifierWithSlice[ctx: P]: P[PyTarget] = {
    P(identifier() ~~ SPACES.? ~~ LSQB ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ RSQB).map { case (target, sliceExpr) => PyTarget(target.name, target.locationString, Some(sliceExpr), target.typeHint) }
      | identifier()
  }

  def identifierWithTypeHint[ctx: P]: P[PyTarget] = {
    P(identifierWithSlice ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ expression_type).map { case (target, typeExpr) => PyTarget(target.name, target.locationString, target.sliceExpr, Some(typeExpr)) }
      | identifierWithSlice
  }

  def target[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    identifierWithTypeHint
  }


  def parameters[ctx: P]: P[Seq[PyAssignment]] = {
    assignment.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?))
  }

  def function_def[ctx: P](state: IndentState): P[PyFunctionDef] = P(
    (ASYNC ~~ SPACES).? ~~ DEF ~~ SPACES ~~ NAME ~~ SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ RPAR ~~ (SPACES.? ~~ RARROW ~~ SPACES.? ~~ expression_type).? ~~ SPACES.? ~~ COLON ~~ NEWLINE ~~ nextExecutionBlock(state)
  ).map { case (async: Option[String], name: String, paramSeq: Seq[PyAssignment], expr_type: Option[PythonType], body) => PyFunctionDef(name, paramSeq.toList, body, async.nonEmpty) }

  def class_def[ctx: P](state: IndentState): P[PyClassDef] = P(
    (ASYNC ~~ SPACES).? ~~ CLASS ~~ SPACES ~~ NAME ~~ (SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ RPAR).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state)
  ).map { case (async: Option[String], name: String, params: Option[Seq[PyAssignment]], body: PyExecutionBlock) => PyClassDef(name, params.map(_.toList).getOrElse(List()), body, async.nonEmpty) }


}
