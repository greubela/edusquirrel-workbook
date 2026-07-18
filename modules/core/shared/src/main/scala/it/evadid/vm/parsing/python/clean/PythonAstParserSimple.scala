package it.evadid.vm.parsing.python.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.generic.abstractions.pipeline.GenericAstScanner
import it.evadid.vm.parsing.python.clean.PyAST.*
import it.evadid.vm.parsing.python.clean.PythonType.*

object PythonAstParserSimple extends GenericAstScanner[PyAST] {

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
  // 2. PROGRAM
  // ==========================================

  override def parseASTFromProgramString(programString: String): Either[Throwable, PyAST] = parse(programString)

  def parse(pythonCode: String): Either[Throwable, PyProgram] = {
    fastparse.parse(pythonCode.trim + "\n", c => py_program(using c)) match {
      case Parsed.Success(astModule, _) => Right(astModule)
      case f: Parsed.Failure => Left(Exception(s"Python Parsing Error: ${f.trace().longAggregateMsg}"))
    }
  }

  def py_program[ctx: P]: P[PyProgram] = P(
    (SPACES.? ~ NEWLINE.? ~ statement(IndentState(0)).rep ~~ NEWLINE.?).map(programFrom)
  )

  // ==========================================
  // 3. SEQUENCES OF STATEMENTS OR EXPRESSIONS
  // ==========================================

  def executionBlock[ctx: P](indentNeeded: Int): P[PyExecutionBlock] = {
    P(indentedStatement(IndentState(indentNeeded)).rep(1).map((res: Seq[Seq[PyStatement]]) => PyExecutionBlock(res.flatten)))
  }

  def expressionList[ctx: P]: P[Seq[PyExpression]] = {
    P(expression.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?)) ~~ (SPACES.? ~~ COMMA).?)
  }

  def parameters[ctx: P]: P[Seq[PyAssignment]] = {
    assignment.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?))
  }

  def targetList[ctx: P]: P[Seq[PyTarget]] = P((target(List()).rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?))) | LPAR ~ targetList ~ RPAR)

  def targets_or_star[ctx: P]: P[Either[Seq[PyTarget], Boolean]] = {
    P(STAR.map(_ => Right(true)) | targetList.map(Left(_)))
  }

  def argument_expressions[ctx: P]: P[Seq[PyExpression]] = P(expression.rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?)))

  // ==========================================
  // 4. STATEMENTS TYPES
  // ==========================================

  def emptyLine[ctx: P]: P[PyStatement] = P(SPACES.? ~~ NEWLINE).map(_ => PyEmptyStatement)

  def indentedStatement[ctx: P](innerState: IndentState): P[Seq[PyStatement]] = {
    P(checkIndent(innerState).! ~~ statement(innerState) ~~ SPACES.? ~~ NEWLINE).map { case (indent: String, stmt: Seq[PyStatement]) => stmt }
      | P(checkIndent(innerState).! ~~ PASS ~~ SPACES.? ~~ NEWLINE).map(_ => List())
      | emptyLine.map(Seq(_))
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

  def import_stmt[ctx: P]: P[PyStatement] = {
    P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES.? ~~ STAR).map { case (name: String) => PyImportFromStatement(name, List(), true) }
      | P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES ~~ targetList).map { case (name, list) => PyImportFromStatement(name, list.toList, false) }
      | P(IMPORT ~~ SPACES ~~ NAME).map { case (moduleName) => PyImportStatement(moduleName) }
  }

  // ==========================================
  // 5. INDIVIDUAL STATEMENTS
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

  // try / except

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

  // Assignments

  def assignment[ctx: P]: P[PyAssignment] = simpleAssignment | augAssignment

  def augAssignment[ctx: P]: P[PyAssignment] = P(
    target() ~~ SPACES.? ~~ AUGASSIGN ~~ SPACES.? ~~ expression
  ).map { case (target: PyTarget, operator: String, expr: PyExpression) => PyAugAssignment(target, operator, expr) }

  def simpleAssignment[ctx: P]: P[PyAssignment] = P(
    target() ~~ SPACES.? ~~ ASSIGN ~~ SPACES.? ~~ expression
  ).map { case (target: PyTarget, expr: PyExpression) => PySimpleAssignment(target, Some(expr)) }

  // Definitions

  def function_def[ctx: P](state: IndentState): P[PyFunctionDef] = P(
    (ASYNC ~~ SPACES).? ~~ DEF ~~ SPACES ~~ NAME ~~ SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ RPAR ~~ (SPACES.? ~~ RARROW ~~ SPACES.? ~~ expression_type).? ~~ SPACES.? ~~ COLON ~~ NEWLINE ~~ nextExecutionBlock(state)
  ).map { case (async: Option[String], name: String, paramSeq: Seq[PyAssignment], expr_type: Option[PythonType[?]], body) => PyFunctionDef(name, paramSeq.toList, body, async.nonEmpty) }

  def class_def[ctx: P](state: IndentState): P[PyClassDef] = P(
    (ASYNC ~~ SPACES).? ~~ CLASS ~~ SPACES ~~ NAME ~~ (SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ RPAR).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state)
  ).map { case (async: Option[String], name: String, params: Option[Seq[PyAssignment]], body: PyExecutionBlock) => PyClassDef(name, params.map(_.toList).getOrElse(List()), body, async.nonEmpty) }


  // ==========================================
  // 7. EXPRESSION TYPES
  // ==========================================

  def expression[ctx: P]: P[PyExpression] = function_call | named_expression | disjunction

  def named_expression[ctx: P]: P[PyExpression] = P(target() ~~ SPACES.? ~~ COLONEQUAL ~~ SPACES.? ~~ expression)
    .map { case (name: PyTarget, expr: PyExpression) => NamedExpression(name.name, expr) }

  def function_call[ctx: P]: P[PyExpression] = P(target() ~~ SPACES.? ~~ LPAR ~~/ SPACES.? ~~ argument_expressions.? ~~ SPACES.? ~~ RPAR)
    .map { case (func: PyTarget, args: Option[Seq[PyExpression]]) => PyFunctionCall(func, args.getOrElse(List()).toList) }

  // ==========================================
  // 8. INDIVIDUAL EXPRESSIONS
  // ==========================================

  private def binaryLeft[ctx: P](next: => P[PyExpression], op: => P[String]): P[PyExpression] =
    P(next ~ (SPACES.? ~ op ~ SPACES.? ~ next).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (operator, right)) => PyOperationBinary(left, operator, right) }
    }

  def disjunction[ctx: P]: P[PyExpression] = binaryLeft(conjunction, OR.!)

  def conjunction[ctx: P]: P[PyExpression] = binaryLeft(inversion, AND.!)

  def inversion[ctx: P]: P[PyExpression] = comparison |
    P(NOT ~ SPACES.? ~ inversion).map(PyOperationUnary("not", _))

  def comparison[ctx: P]: P[PyExpression] = binaryLeft(bitwise_or, COMPAREOP)

  def bitwise_or[ctx: P]: P[PyExpression] = binaryLeft(bitwise_xor, VBAR.!)

  def bitwise_xor[ctx: P]: P[PyExpression] = binaryLeft(bitwise_and, CIRCUMFLEX.!)

  def bitwise_and[ctx: P]: P[PyExpression] = binaryLeft(shift_expr, AMPER.!)

  def shift_expr[ctx: P]: P[PyExpression] = binaryLeft(sum, SHIFTOP)

  def sum[ctx: P]: P[PyExpression] = binaryLeft(term, PLUS.! | MINUS.!)

  def term[ctx: P]: P[PyExpression] = binaryLeft(factor, MULTLIKEOP)

  def factor[ctx: P]: P[PyExpression] =
    P(UNARYPREFIX ~ SPACES.? ~ factor).map(PyOperationUnary(_, _)) | power

  def power[ctx: P]: P[PyExpression] =
    P(primary ~ (SPACES.? ~ DOUBLESTAR.! ~ SPACES.? ~ factor).?).map {
      case (base, Some((operator, exponent))) => PyOperationBinary(base, operator, exponent)
      case (base, None) => base
    }

  def parenthesizedExpression[ctx: P]: P[PyExpression] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ RPAR)

  def listLiteral[ctx: P]: P[PyListLiteral] =
    P(LSQB ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RSQB)
      .map(elements => PyListLiteral(elements.getOrElse(List()).toList))

  def tupleLiteral[ctx: P]: P[PyTupleLiteral] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ COMMA ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RPAR)
      .map { case (head, tail) => PyTupleLiteral((head +: tail.getOrElse(List())).toList) }

  // ==========================================
  // 8. ATOMAR SEQUENCES
  // ==========================================

  def primary[ctx: P]: P[PyExpression] = P(listLiteral | tupleLiteral | parenthesizedExpression | target() | literal)

  def target[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    identifierWithTypeHint
  }

  def identifierWithTypeHint[ctx: P]: P[PyTarget] = {
    P(identifierWithSlice ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ expression_type).map { case (target, typeExpr) => PyTarget(target.name, target.locationString, target.sliceExpr, Some(typeExpr)) }
      | identifierWithSlice
  }

  def identifierWithSlice[ctx: P]: P[PyTarget] = {
    P(identifier() ~~ SPACES.? ~~ LSQB ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ RSQB).map { case (target, sliceExpr) => PyTarget(target.name, target.locationString, Some(sliceExpr), target.typeHint) }
      | identifier()
  }

  def identifier[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    P(NAME ~~ SPACES.? ~~ DOT ~~ SPACES.? ~~ identifier(knownContext)).map { case (name: String, target: PyTarget) => PyTarget(target.name, List(name) ++ target.locationString, target.sliceExpr, target.typeHint) }
      | NAME.map(PyTarget(_))
  }


  // ==========================================
  // 9. Value Literals
  // ==========================================


  def decinteger[ctx: P]: P[PythonLiteral[BigInt]] = P((nonzero_digit ~~ (P("_").? ~~ digit).rep | P("0").rep(1, sep = P("_").?)).!).map(PYTHON_INTEGER().createLiteralUnsafe)

  def bininteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("bB") ~~ (P("_").? ~~ bindigit).rep(1)).!.map(PYTHON_INTEGER_BIN().createLiteralUnsafe)

  def octinteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("oO") ~~ (P("_").? ~~ octdigit).rep(1)).!.map(PYTHON_INTEGER_OCT().createLiteralUnsafe)

  def hexinteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("xX") ~~ (P("_").? ~~ hexdigit).rep(1)).!.map(PYTHON_INTEGER_HEX().createLiteralUnsafe)

  def INTEGER[ctx: P]: P[PythonLiteral[BigInt]] = P(hexinteger | octinteger | bininteger | decinteger)

  def pointfloat[ctx: P]: P[String] = P(digitpart.? ~~ fraction | digitpart ~~ ".").!

  def expfloat[ctx: P]: P[String] = P((digitpart | pointfloat) ~~ exponent).!

  def FLOAT_NUMBER[ctx: P]: P[PythonLiteral[Double]] = P(expfloat | pointfloat).!.map(PYTHON_FLOAT().createLiteralUnsafe)

  def NUMBER[ctx: P]: P[PythonLiteral[?]] = P(FLOAT_NUMBER | INTEGER)

  def FALSE[ctx: P]: P[PythonLiteral[Boolean]] = P("False" ~~ !ID_CONTINUE).!.map(PYTHON_BOOL().createLiteralUnsafe)

  def TRUE[ctx: P]: P[PythonLiteral[Boolean]] = P("True" ~~ !ID_CONTINUE).!.map(PYTHON_BOOL().createLiteralUnsafe)

  def NONE_LITERAL[ctx: P]: P[PythonLiteral[?]] = P(NONE.!).map(PYTHON_NONE().createLiteralUnsafe)

  def literal[ctx: P]: P[PythonLiteral[?]] = P(NUMBER | TRUE | FALSE | NONE_LITERAL | STRING_LITERAL)

  def STRING_LITERAL[ctx: P]: P[PythonLiteral[?]] = P(STRING).!.map(PYTHON_STRING().createLiteralUnsafe)


  // ==========================================
  // 10. Type Literals
  // ==========================================

  def atomic_type[ctx: P]: P[PythonType[?]] = {
    P("bool").!.map(_ => PYTHON_BOOL())
      | P("Any").!.map(_ => PYTHON_ANY())
      // | P("function").!.map(_ => PYTHON_FUNCTION)
      | P("str").!.map(_ => PYTHON_STRING())
      | P("int").!.map(_ => PYTHON_INTEGER())
      | P("float").!.map(_ => PYTHON_FLOAT())
      | NAME.map(str => PYTHON_UNPARSABLE_TYPE(str))
  }

  def expression_type[ctx: P]: P[PythonType[?]] = {
    P(
      (atomic_type ~~ SPACES.? ~~ VBAR ~~ SPACES.? ~~ expression_type).map { case (t1, t2) => PYTHON_UNION_TYPE(t1, t2) }
        | atomic_type)
  }


}
