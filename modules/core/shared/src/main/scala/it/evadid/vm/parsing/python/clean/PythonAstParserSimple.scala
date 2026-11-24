package it.evadid.vm.parsing.python.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.generic.abstractions.pipeline.GenericAstScanner
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import it.evadid.vm.parsing.python.clean.model.*

object PythonAstParserSimple extends GenericAstScanner[PyAST] {

  // ==========================================
  // 1. HELPER
  // ==========================================
  def programFrom(stmts: Seq[Seq[PyStatement]]): PyProgram = {
    val tups = stmts.zipWithIndex.flatMap((lineStmts: Seq[PyStatement], lineNr: Int) => lineStmts.map((lineNr, _)))
    PyProgram(tups.map((lineNr, stmt) => StatementWithLineNumber(stmt, lineNr)))
  }

  /** Exactly the block's indentation; deeper lines belong to nested blocks. */
  def checkIndent[ctx: P](state: IndentState): P[String] = P(" ".rep(exactly = state.currentLevel).! ~~ !" ")

  private def INLINE_WS[ctx: P]: P[Unit] = P(CharsWhileIn(" \t", 0))

  /** A block is indented like its first line that is neither blank nor only a comment. */
  def nextExecutionBlock[ctx: P](state: IndentState): P[PyExecutionBlock] = {
    val input = summon[P[Any]].input
    P(Index).flatMapX { start =>
      blockIndent(input.slice(start, input.length)) match {
        case Some(indent) if indent > state.currentLevel => executionBlock(indent)
        case _ => Fail
      }
    }
  }

  private def blockIndent(rest: String): Option[Int] =
    rest.split("\n", -1).iterator.map(line => line.takeWhile(_ == ' ').length -> line.trim)
      .find((_, content) => content.nonEmpty && !content.startsWith("#")).map(_._1)

  private def sourceBetween[ctx: P](start: Int, end: Int): String =
    summon[P[Any]].input.slice(start, end).replaceAll("\\s+$", "")

  // ==========================================
  // 2. PROGRAM
  // ==========================================

  override def parseASTFromProgramString(programString: String): Either[Throwable, PyAST] = parse(programString)

  /** Unix line endings and four spaces per tab, as Python's tokenizer expands them for indentation. */
  def normalizeLineEndingsAndDetab(source: String): String =
    source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")

  /** Removes the indentation all non-blank lines share, as an indented fragment carries it. */
  def dedent(source: String): String = {
    val lines = normalizeLineEndingsAndDetab(source).split("\n", -1).toVector
    val shared = lines.filter(_.trim.nonEmpty).map(_.takeWhile(_ == ' ').length).minOption.getOrElse(0)
    lines.map(_.drop(shared)).mkString("\n")
  }

  def parse(pythonCode: String): Either[Throwable, PyProgram] = {
    val trimmed = dedent(pythonCode).trim
    fastparse.parse(trimmed + "\n", c => py_program(using c)) match {
      case Parsed.Success(astModule, _) => Right(astModule)
      case f: Parsed.Failure =>
        val (line, column) = ProgrammingConversionLocation.inTrimmed(pythonCode, trimmed, f.index)
        Left(ProgrammingConversionException.unparsable(
          s"Python Parsing Error: ${f.trace().longAggregateMsg}", Some(line), Some(column)))
    }
  }

  def py_program[ctx: P]: P[PyProgram] = P(
    (NEWLINE.rep ~~ statement(IndentState(0)).rep ~~ End).map(programFrom)
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
    P(parameter.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?)))
  }

  private def parameter[ctx: P]: P[PyAssignment] =
    P(target() ~~ (SPACES.? ~~ ASSIGN ~~ SPACES.? ~~ expression).?).map { (target, value) =>
      PySimpleAssignment(target, value)
    }

  def targetList[ctx: P]: P[Seq[PyTarget]] = P((target(List()).rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?))) | LPAR ~ targetList ~ RPAR)

  def targets_or_star[ctx: P]: P[Either[Seq[PyTarget], Boolean]] = {
    P(STAR.map(_ => Right(true)) | targetList.map(Left(_)))
  }

  def argument_expressions[ctx: P]: P[Seq[PyExpression]] = P(argument.rep(sep = P(SPACES.? ~ COMMA ~ SPACES.?)))

  /** A positional argument or a keyword argument such as `file=sys.stderr` (not `a == b`). */
  private def argument[ctx: P]: P[PyExpression] =
    P((NAME ~~ SPACES.? ~~ "=" ~~ !"=" ~~ SPACES.? ~~ expression).map(PyKeywordArgument(_, _)) | expression)

  // ==========================================
  // 4. STATEMENTS TYPES
  // ==========================================

  def emptyLine[ctx: P]: P[PyStatement] = P(INLINE_WS ~~ NEWLINE).map(_ => PyEmptyStatement)

  def indentedStatement[ctx: P](innerState: IndentState): P[Seq[PyStatement]] = {
    P(" ".rep(innerState.currentLevel) ~~ commentLine).map(Seq(_))
      | P(checkIndent(innerState) ~~ statement(innerState)).map { case (indent: String, stmt: Seq[PyStatement]) => stmt }
      | emptyLine.map(Seq(_))
  }

  def commentLine[ctx: P]: P[PyCommentStatement] =
    P(INLINE_WS ~~ "#" ~~ CharsWhile(_ != '\n', 0).! ~~ NEWLINE).map(text => PyCommentStatement(text.trim))

  private def trailingComment[ctx: P]: P[Option[PyCommentStatement]] =
    P(INLINE_WS ~~ ("#" ~~ CharsWhile(_ != '\n', 0).!).? ~~ INLINE_WS).map(_.map(text => PyCommentStatement(text.trim)))

  def statement[ctx: P](state: IndentState): P[Seq[PyStatement]] = P(
    commentLine.map(Seq(_))
      | (simple_stmt(state).rep(1, sep = P(SPACES ~~ SEMI ~~ SPACES)) ~~ (SPACES ~~ SEMI).? ~~ trailingComment ~~ NEWLINE)
        .map((stmts: Seq[PyStatement], comment: Option[PyCommentStatement]) => stmts ++ comment)
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
    import_stmt | assignment | declaration | emptyLine
      | P(PASS).map(_ => PyPassStatement)
      | P(BREAK).map(_ => PyBreakStatement)
      | P(CONTINUE).map(_ => PyContinueStatement)
      | P(RETURN ~~ (SPACES ~~ expression).?).map { expr => PyReturnStatement(expr) }
      | P(RAISE ~~ (SPACES ~~ expression).? ~~ (SPACES ~~ FROM ~~ SPACES ~~ expression).?).map { case (e, f) => PyRaiseStatement(e, f) }
      | expression
    /*type_alias |  del_stmt |    assert_stmt |  break_stmt | continue_stmt | expression.map(ExprStmt(_))*/
  )

  def import_stmt[ctx: P]: P[PyStatement] = {
    P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES.? ~~ STAR).map { case (name: String) => PyImportFromStatement(name, List(), true) }
      | P(FROM ~~ SPACES ~~ NAME ~~ SPACES ~~ IMPORT ~~ SPACES ~~ targetList).map { case (name, list) => PyImportFromStatement(name, list.toList, false) }
      | P(IMPORT ~~ SPACES ~~ NAME).map { case (moduleName) => PyPlainImportStatement(moduleName) }
  }

  // ==========================================
  // 5. INDIVIDUAL STATEMENTS
  // ==========================================

  // IF / ELIF / ELSE
  def ifStmt[ctx: P](state: IndentState): P[PyIfStatement] = P(
    IF ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ P(elifBranches(state) | elseBranch(state)).?
  ).map { case (expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyIfStatement(expr, body, orelse.getOrElse(passBlock)) }

  def elifBranches[ctx: P](state: IndentState): P[PyExecutionBlock] = P(
    checkIndent(state) ~~ ELIF ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ P(elifBranches(state) | elseBranch(state)).?)
    .map { case (indent: String, expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyExecutionBlock(List(PyIfStatement(expr, body, orelse.getOrElse(passBlock)))) }

  def elseBranch[ctx: P](state: IndentState): P[PyExecutionBlock] = P(
    checkIndent(state) ~~ ELSE ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state))
    .map { case (indent: String, elseBlock: PyExecutionBlock) => elseBlock }

  // WHILE / FOR / ASYNC FOR
  def whileStmt[ctx: P](state: IndentState): P[PyWhileStatement] = P(
    WHILE ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ elseBranch(state).?
  ).map { case (expr: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyWhileStatement(expr, body, orelse) }

  def forStmt[ctx: P](state: IndentState, isAsync: Boolean = false): P[PyForStatement] = P(
    (ASYNC ~~ SPACES.?).? ~~ FOR ~~ SPACES ~~ loopTarget ~~ SPACES ~~ IN ~~ SPACES ~~ expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ elseBranch(state).?
  ).map { case (async: Option[String], expr1: PyExpression, expr2: PyExpression, body: PyExecutionBlock, orelse: Option[PyExecutionBlock]) => PyForStatement(expr1, expr2, body, orelse, async.nonEmpty) }

  /** A loop variable; a full expression would read `x in range(3)` as a membership test. */
  private def loopTarget[ctx: P]: P[PyExpression] = P(("_" ~~ !ID_CONTINUE).!.map(PyTarget(_)) | targetAtom)

  // try / except

  def tryStmt[ctx: P](state: IndentState): P[PyTryStatement] = P(
    Index ~~ TRY ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ exceptHandler(state).rep ~~ NEWLINE.? ~~ elseBranch(state).? ~~ finallyBlock(state).? ~~ Index
  ).map { case (start, body: PyExecutionBlock, handlers: Seq[PyExceptClause], orelse: Option[PyExecutionBlock], finalBody: Option[PyExceptClauseFinally], end) =>
    PyTryStatement(body, (handlers ++ finalBody).toList, orelse, sourceBetween(start, end)) }

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

  /** An annotated name without a value, e.g. `x: float`. */
  def declaration[ctx: P]: P[PyAssignment] = P(
    identifier() ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ expression_type ~~ !(SPACES.? ~~ ASSIGN)
  ).map { case (target: PyTarget, typeHint: PythonType[?]) => PySimpleAssignment(target.copy(typeHint = Some(typeHint)), None) }

  def simpleAssignment[ctx: P]: P[PyAssignment] = P(
    target() ~~ SPACES.? ~~ ASSIGN ~~ SPACES.? ~~ expression
  ).map { case (target: PyTarget, expr: PyExpression) => PySimpleAssignment(target, Some(expr)) }

  // Definitions

  private def decorator[ctx: P](state: IndentState): P[String] =
    P("@" ~~ NAME ~~ INLINE_WS ~~ NEWLINE ~~ checkIndent(state)).map(_._1)

  def function_def[ctx: P](state: IndentState): P[PyFunctionDef] = P(
    Index ~~ decorator(state).rep ~~ (ASYNC ~~ SPACES).? ~~ DEF ~~ SPACES ~~ NAME ~~ SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ (COMMA ~~ SPACES.?).? ~~ RPAR ~~ (SPACES.? ~~ RARROW ~~ SPACES.? ~~ expression_type).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ Index
  ).map { case (start, decorators, async: Option[String], name: String, paramSeq: Seq[PyAssignment], returnType: Option[PythonType[?]], body, end) =>
    PyFunctionDef(name, paramSeq.toList, body, async.nonEmpty, returnType, decorators.toList, sourceBetween(start, end)) }

  def class_def[ctx: P](state: IndentState): P[PyClassDef] = P(
    Index ~~ (ASYNC ~~ SPACES).? ~~ CLASS ~~ SPACES ~~ NAME ~~ (SPACES.? ~~ LPAR ~~ SPACES.? ~~ parameters ~~ SPACES.? ~~ RPAR).? ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ NEWLINE ~~ nextExecutionBlock(state) ~~ Index
  ).map { case (start, async: Option[String], name: String, params: Option[Seq[PyAssignment]], body: PyExecutionBlock, end) =>
    PyClassDef(name, params.map(_.toList).getOrElse(List()), body, async.nonEmpty, sourceBetween(start, end)) }


  // ==========================================
  // 7. EXPRESSION TYPES
  // ==========================================

  def expression[ctx: P]: P[PyExpression] = lambdaExpression | named_expression | disjunction

  /** Lambdas are kept as source text; the shared tree has no anonymous functions. */
  def lambdaExpression[ctx: P]: P[PyExpression] =
    P(("lambda" ~~ !ID_CONTINUE ~~ CharsWhile(_ != ':', 0) ~~ ":" ~~ SPACES.? ~~ disjunction).!).map(PyRawExpression(_))

  def named_expression[ctx: P]: P[PyExpression] = P(target() ~~ SPACES.? ~~ COLONEQUAL ~~ SPACES.? ~~ expression)
    .map { case (name: PyTarget, expr: PyExpression) => NamedExpression(name.name, expr) }

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

  /** Python chains comparisons: `a < b < c` means `a < b and b < c`. */
  def comparison[ctx: P]: P[PyExpression] =
    P(bitwise_or ~ (SPACES.? ~ COMPAREOP ~ SPACES.? ~ bitwise_or).rep).map { case (first, rest) =>
      val operands = first +: rest.map(_._2)
      rest.zipWithIndex.map { case ((operator, right), index) => PyOperationBinary(operands(index), operator, right) }
        .reduceLeftOption[PyExpression]((left, right) => PyOperationBinary(left, "and", right)).getOrElse(first)
    }

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

  private def parenthesizedOrTuple[ctx: P]: P[PyExpression] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ (COMMA ~~ SPACES.? ~~ expressionList).? ~~ SPACES.? ~~ RPAR)
      .map { (head, tail) => tail match {
        case None => head
        case Some(rest) =>
          val values = (head +: rest).toList
          PythonLiteral(collectionLiteralValue("(", values, ")"), PYTHON_LIST(commonExpressionType(values)))
      } }

  // ==========================================
  // 8. ATOMAR SEQUENCES
  // ==========================================

  def primary[ctx: P]: P[PyExpression] = P(atom ~~ trailer.rep).map { case (base, trailers) =>
    trailers.foldLeft(base) { case (receiver, applyTrailer) => applyTrailer(receiver) }
  }

  def atom[ctx: P]: P[PyExpression] = P(listLiteral | dictLiteral | setLiteral | parenthesizedOrTuple | literal | targetAtom)

  private def trailer[ctx: P]: P[PyExpression => PyExpression] = P(attributeTrailer | callTrailer | subscriptTrailer)

  private def attributeTrailer[ctx: P]: P[PyExpression => PyExpression] =
    P(SPACES.? ~~ DOT ~~ SPACES.? ~~ NAME).map(name => receiver => PyAttributeAccess(receiver, name))

  private def callTrailer[ctx: P]: P[PyExpression => PyExpression] =
    P(SPACES.? ~~ LPAR ~~ SPACES.? ~~ argument_expressions.? ~~ SPACES.? ~~ RPAR).map { args => receiver =>
      receiver match {
        case target: PyTarget => PyFunctionCall(target, args.getOrElse(List()).toList)
        case callee => PyCallExpression(callee, args.getOrElse(List()).toList)
      }
    }

  private def subscriptTrailer[ctx: P]: P[PyExpression => PyExpression] =
    P(SPACES.? ~~ LSQB ~~ SPACES.? ~~ expressionList ~~ SPACES.? ~~ RSQB).map(indices => receiver => PySubscript(receiver, indices.toList))

  private def targetAtom[ctx: P]: P[PyTarget] = NAME.map(PyTarget(_))

  def target[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    identifierWithTypeHint
  }

  def identifierWithTypeHint[ctx: P]: P[PyTarget] = {
    P(identifierWithSlice ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ expression_type).map { case (target, typeExpr) => target.copy(typeHint = Some(typeExpr)) }
      | identifierWithSlice
  }

  def identifierWithSlice[ctx: P]: P[PyTarget] = {
    P(identifier() ~~ SPACES.? ~~ LSQB ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ RSQB).map { case (target, sliceExpr) => PyTarget(target.identifier, target.locationString, Some(sliceExpr), target.typeHint) }
      | identifier()
  }

  def identifier[ctx: P](knownContext: List[String] = List()): P[PyTarget] = {
    P(NAME ~~ SPACES.? ~~ DOT ~~ SPACES.? ~~ identifier(knownContext)).map { case (name: String, target: PyTarget) => PyTarget(target.identifier, List(name) ++ target.locationString, target.sliceExpr, target.typeHint) }
      | NAME.map(PyTarget(_))
  }


  // ==========================================
  // 9. Value Literals
  // ==========================================


  private def expressionLiteralValue(expr: PyExpression): String = expr match {
    case literal: PythonLiteral[?] => literal.literalValue
    case target: PyTarget => target.name
    case other => other.toString
  }

  private def collectionLiteralValue(open: String, expressions: List[PyExpression], close: String): String =
    expressions.map(expressionLiteralValue).mkString(open, ", ", close)

  private def dictLiteralValue(entries: List[(PyExpression, PyExpression)]): String =
    entries.map { case (key, value) => s"${expressionLiteralValue(key)}: ${expressionLiteralValue(value)}" }.mkString("{", ", ", "}")

  def listLiteral[ctx: P]: P[PythonLiteral[?]] =
    P(LSQB ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RSQB)
      .map { elements =>
        val values = elements.getOrElse(List()).toList
        PythonLiteral(collectionLiteralValue("[", values, "]"), PYTHON_LIST(commonExpressionType(values)))
      }

  def tupleLiteral[ctx: P]: P[PythonLiteral[?]] =
    P(LPAR ~~ SPACES.? ~~ expression ~~ SPACES.? ~~ COMMA ~~ SPACES.? ~~ expressionList.? ~~ SPACES.? ~~ RPAR)
      .map { case (head, tail) =>
        val values = (head +: tail.getOrElse(List())).toList
        PythonLiteral(collectionLiteralValue("(", values, ")"), PYTHON_LIST(commonExpressionType(values)))
      }

  private def dictEntry[ctx: P]: P[(PyExpression, PyExpression)] =
    P(expression ~~ SPACES.? ~~ COLON ~~ SPACES.? ~~ expression)

  def dictLiteral[ctx: P]: P[PythonLiteral[?]] =
    P(LBRACE ~~ SPACES.? ~~ dictEntry.rep(sep = P(SPACES.? ~~ COMMA ~~ SPACES.?)) ~~ (SPACES.? ~~ COMMA).? ~~ SPACES.? ~~ RBRACE)
      .map { entries =>
        val entryList = entries.toList
        PythonLiteral(
          dictLiteralValue(entryList),
          PYTHON_DICT(
            commonExpressionType(entryList.map(_._1)),
            commonExpressionType(entryList.map(_._2))
          )
        )
      }

  def setLiteral[ctx: P]: P[PythonLiteral[?]] =
    P(LBRACE ~~ SPACES.? ~~ expressionList ~~ SPACES.? ~~ RBRACE)
      .map { elements =>
        val values = elements.toList
        PythonLiteral(collectionLiteralValue("{", values, "}"), PYTHON_SET(commonExpressionType(values)))
      }

  private def expressionType(expr: PyExpression): Option[PythonType[?]] = expr match {
    case literal: PythonLiteral[?] => Some(literal.literalType)
    case target: PyTarget => target.typeHint
    case _ => None
  }

  private def commonExpressionType(expressions: List[PyExpression]): PythonType[Any] = {
    val expressionTypes = expressions.flatMap(expressionType)
    expressionTypes.headOption
      .filter(first => expressionTypes.size == expressions.size && expressionTypes.forall(_.typenameInCode == first.typenameInCode))
      .getOrElse(PYTHON_ANY())
      .asInstanceOf[PythonType[Any]]
  }

  def decinteger[ctx: P]: P[PythonLiteral[BigInt]] = P((nonzero_digit ~~ (P("_").? ~~ digit).rep | P("0").rep(1, sep = P("_").?)).!).map(raw => PythonLiteral(raw, PYTHON_INTEGER()))

  def bininteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("bB") ~~ (P("_").? ~~ bindigit).rep(1)).!.map(raw => PythonLiteral(raw, PYTHON_INTEGER_BIN()))

  def octinteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("oO") ~~ (P("_").? ~~ octdigit).rep(1)).!.map(raw => PythonLiteral(raw, PYTHON_INTEGER_OCT()))

  def hexinteger[ctx: P]: P[PythonLiteral[BigInt]] = P("0" ~~ CharIn("xX") ~~ (P("_").? ~~ hexdigit).rep(1)).!.map(raw => PythonLiteral(raw, PYTHON_INTEGER_HEX()))

  def INTEGER[ctx: P]: P[PythonLiteral[BigInt]] = P(hexinteger | octinteger | bininteger | decinteger)

  def pointfloat[ctx: P]: P[String] = P(digitpart.? ~~ fraction | digitpart ~~ ".").!

  def expfloat[ctx: P]: P[String] = P((pointfloat | digitpart) ~~ exponent).!

  def FLOAT_NUMBER[ctx: P]: P[PythonLiteral[Double]] = P(expfloat | pointfloat).!.map(raw => PythonLiteral(raw, PYTHON_FLOAT()))

  def NUMBER[ctx: P]: P[PythonLiteral[?]] = P(FLOAT_NUMBER | INTEGER)

  def FALSE[ctx: P]: P[PythonLiteral[Boolean]] = P("False" ~~ !ID_CONTINUE).!.map(raw => PythonLiteral(raw, PYTHON_BOOL()))

  def TRUE[ctx: P]: P[PythonLiteral[Boolean]] = P("True" ~~ !ID_CONTINUE).!.map(raw => PythonLiteral(raw, PYTHON_BOOL()))

  def NONE_LITERAL[ctx: P]: P[PythonLiteral[?]] = P(NONE.!).map(raw => PythonLiteral(raw, PYTHON_NONE()))

  def literal[ctx: P]: P[PythonLiteral[?]] = P(NUMBER | TRUE | FALSE | NONE_LITERAL | STRING_LITERAL)

  def STRING_LITERAL[ctx: P]: P[PythonLiteral[?]] = P(STRING).!.map(raw => PythonLiteral(raw, PYTHON_STRING()))


  // ==========================================
  // 10. Type Literals
  // ==========================================

  def collection_type[ctx: P]: P[PythonType[?]] = {
    P("list" ~~ LSQB ~~ SPACES.? ~~ expression_type ~~ SPACES.? ~~ RSQB).map(PYTHON_LIST(_))
      | P("array" ~~ LSQB ~~ SPACES.? ~~ expression_type ~~ SPACES.? ~~ RSQB).map(PYTHON_ARRAY(_))
      | P("set" ~~ LSQB ~~ SPACES.? ~~ expression_type ~~ SPACES.? ~~ RSQB).map(PYTHON_SET(_))
      | P("dict" ~~ LSQB ~~ SPACES.? ~~ expression_type ~~ SPACES.? ~~ COMMA ~~ SPACES.? ~~ expression_type ~~ SPACES.? ~~ RSQB).map { case (keyType, valueType) => PYTHON_DICT(keyType, valueType) }
  }

  def atomic_type[ctx: P]: P[PythonType[?]] = {
    P(collection_type
      | P(NONE).map(_ => PYTHON_NONE())
      | P("bool" ~~ !ID_CONTINUE).map(_ => PYTHON_BOOL())
      | P("Any" ~~ !ID_CONTINUE).map(_ => PYTHON_ANY())
      | P("str" ~~ !ID_CONTINUE).map(_ => PYTHON_STRING())
      | P("int" ~~ !ID_CONTINUE).map(_ => PYTHON_INTEGER())
      | P("float" ~~ !ID_CONTINUE).map(_ => PYTHON_FLOAT())
      | NAME.map(str => PYTHON_UNPARSABLE_TYPE(str)))
  }

  def expression_type[ctx: P]: P[PythonType[?]] = {
    P(
      (atomic_type ~~ SPACES.? ~~ VBAR ~~ SPACES.? ~~ expression_type).map { case (t1, t2) => PYTHON_UNION_TYPE(t1, t2) }
        | atomic_type)
  }


}
