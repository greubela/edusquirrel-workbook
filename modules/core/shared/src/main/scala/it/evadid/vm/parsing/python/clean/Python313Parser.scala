package it.evadid.vm.parsing.python.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.python.clean.PyAST
import it.evadid.vm.parsing.python.clean.PyAST.*

object Python313Parser {

  // ==========================================
  // 1. Structural Tokens & Structural Lexer Rules
  // ==========================================
  def LPAR[ctx: P]: P[Unit] = P("(")

  def RPAR[ctx: P]: P[Unit] = P(")")

  def LSQB[ctx: P]: P[Unit] = P("[")

  def RSQB[ctx: P]: P[Unit] = P("]")

  def LBRACE[ctx: P]: P[Unit] = P("{")

  def RBRACE[ctx: P]: P[Unit] = P("}")

  def DOT[ctx: P]: P[Unit] = P(".")

  def COLON[ctx: P]: P[Unit] = P(":")

  def COMMA[ctx: P]: P[Unit] = P(",")

  def SEMI[ctx: P]: P[Unit] = P(";")

  def STAR[ctx: P]: P[Unit] = P("*")

  def DOUBLESTAR[ctx: P]: P[Unit] = P("**")

  def EQUAL[ctx: P]: P[Unit] = ??? // P("=" ~ !"=")

  def RARROW[ctx: P]: P[Unit] = P("->")

  def COLONEQUAL[ctx: P]: P[Unit] = P(":=")

  def AUGASSIGN[ctx: P]: P[String] = P(StringIn("+=", "-=", "*=", "@=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", "**=", "//=")).!

  // ==========================================
  // 2. Comprehensive Python Keywords Mapping
  // ==========================================


  def LAMBDA[ctx: P]: P[Unit] = ??? // P("lambda" ~~ !ID_CONTINUE)

  def NAME_OR_TYPE[ctx: P]: P[Unit] = ??? // P("type" ~~ !ID_CONTINUE)

  def NAME_OR_MATCH[ctx: P]: P[Unit] = ??? // P("match" ~~ !ID_CONTINUE)

  def NAME_OR_CASE[ctx: P]: P[Unit] = ??? // P("case" ~~ !ID_CONTINUE)

  def NAME_OR_WILDCARD[ctx: P]: P[Unit] = ??? // P("_" ~~ !ID_CONTINUE)

  // ==========================================
  // 3. Structural Tokens & Whitespace Mapping
  // ==========================================
  def ID_START[ctx: P]: P[Unit] = P(CharIn("a-zA-Z_"))

  def ID_CONTINUE[ctx: P]: P[Unit] = P(CharIn("a-zA-Z0-9_"))

  def NAME[ctx: P]: P[String] = P(ID_START ~~ ID_CONTINUE.rep).!

  def dotted_name[ctx: P]: P[String] = P(NAME.rep(1, sep = ".")).!

  def INTEGER[ctx: P]: P[Unit] = P(CharIn("0-9").rep(1))

  def FLOAT_NUMBER[ctx: P]: P[Unit] = P(INTEGER ~~ "." ~~ INTEGER.? | "." ~~ INTEGER)

  def NUMBER[ctx: P]: P[String] = P(FLOAT_NUMBER | INTEGER).!

  def STRING[ctx: P]: P[String] = P(
    CharIn("fFrR").rep(max = 2) ~~ (
      "\"\"\"" ~~ CharsWhile(_ != '"', 0) ~~ "\"\"\"" |
        "'''" ~~ CharsWhile(_ != '\'', 0) ~~ "'''" |
        "\"" ~~ CharsWhile(_ != '"', 0) ~~ "\"" |
        "'" ~~ CharsWhile(_ != '\'', 0) ~~ "'"
      )
  ).!

  def NEWLINE[ctx: P]: P[Unit] = P("\r".? ~~ "\n")

  def COMMENT[ctx: P]: P[Unit] = P("#" ~~ CharsWhile(_ != '\n', 0))

  def WS[ctx: P]: P[Unit] = P(CharIn(" \t\f").rep(1))

  def SPACES[ctx: P]: P[Unit] = P((WS | COMMENT).rep)

  case class IndentState(var currentLevel: Int = 0)

  def parseWithIndent[T](indent: Int, p: => P[T])(implicit ctx: P[?]): P[T] = {
    val spaces = ctx.input.slice(ctx.index, ctx.index + indent)
    if (spaces.forall(_ == ' ') && spaces.length == indent) {
      ctx.freshSuccess(null)
      p
    } else {
      ctx.freshFailure()
    }
  }

  // ==========================================
  // 4. Fully Layered Expressions Precedence Engine
  // ==========================================
  def expression[ctx: P]: P[PyAST] = P(named_expression)

  def expressions[ctx: P]: P[Seq[PyAST]] = P(expression.rep(1, sep = P(SPACES ~ COMMA ~ SPACES)) ~ (SPACES ~ COMMA).?)

  def named_expression[ctx: P]: P[PyAST] = P(
    P(NAME.map(Name(_)) ~ SPACES ~ COLONEQUAL ~ SPACES ~ expression).map { case (t, v) => NamedExpr(t, v) } |
      disjunction
  )

  def disjunction[ctx: P]: P[PyAST] = P(conjunction ~ (SPACES ~ OR ~ SPACES ~ conjunction).rep).map {
    case (head, Seq()) => head
    case (head, tail) => BoolOp("or", head +: tail)
  }

  def conjunction[ctx: P]: P[PyAST] = P(inversion ~ (SPACES ~ AND ~ SPACES ~ inversion).rep).map {
    case (head, Seq()) => head
    case (head, tail) => BoolOp("and", head +: tail)
  }

  def inversion[ctx: P]: P[PyAST] = P(
    (NOT ~ SPACES ~ inversion).map(UnaryOp("not", _)) |
      comparison
  )

  def comparison[ctx: P]: P[PyAST] = P(bitwise_or ~ (SPACES ~ compare_op ~ SPACES ~ bitwise_or).rep).map {
    case (head, Seq()) => head
    case (head, tail) => Compare(head, tail.map(_._1), tail.map(_._2))
  }

  def compare_op[ctx: P]: P[String] = P(
    P("==").! | P("!=").! | P("<=").! | P(">=").! | P("<").! | P(">").! |
      P(NOT ~ SPACES ~ IN).map(_ => "not in") | P(IN).!.map(_ => "in") |
      P(IS ~ SPACES ~ NOT).map(_ => "is not") | P(IS).!.map(_ => "is")
  )

  def bitwise_or[ctx: P]: P[PyAST] = P(bitwise_xor ~ (SPACES ~ "|" ~ SPACES ~ bitwise_xor).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, r) => BinOp(l, "|", r) }
  }

  def bitwise_xor[ctx: P]: P[PyAST] = P(bitwise_and ~ (SPACES ~ "^" ~ SPACES ~ bitwise_and).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, r) => BinOp(l, "^", r) }
  }

  def bitwise_and[ctx: P]: P[PyAST] = P(shift_expr ~ (SPACES ~ "&" ~ SPACES ~ shift_expr).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, r) => BinOp(l, "&", r) }
  }

  def shift_expr[ctx: P]: P[PyAST] = P(sum ~ (SPACES ~ StringIn("<<", ">>").! ~ SPACES ~ sum).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, (op, r)) => BinOp(l, op, r) }
  }

  def sum[ctx: P]: P[PyAST] = P(term ~ (SPACES ~ StringIn("+", "-").! ~ SPACES ~ term).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, (op, r)) => BinOp(l, op, r) }
  }

  def term[ctx: P]: P[PyAST] = P(factor ~ (SPACES ~ StringIn("*", "/", "//", "%", "@").! ~ SPACES ~ factor).rep).map {
    case (head, tail) => tail.foldLeft(head) { case (l, (op, r)) => BinOp(l, op, r) }
  }

  def factor[ctx: P]: P[PyAST] = P(
    (StringIn("+", "-", "~").! ~ SPACES ~ factor).map { case (op, f) => UnaryOp(op, f) } |
      power
  )

  def power[ctx: P]: P[PyAST] = P(primary ~ (SPACES ~ "**" ~ SPACES ~ factor).?).map {
    case (base, None) => base
    case (base, Some(p)) => BinOp(base, "**", p)
  }

  // --- Collection Initializers & Comprehensions Layout Integration ---
  def for_if_clause[ctx: P]: P[Comprehension] = P(
    (ASYNC.?.map(_.isDefined).!.map(_.toBoolean) ~ SPACES ~ FOR ~ SPACES ~ expression ~ SPACES ~ IN ~ SPACES ~ disjunction ~ (SPACES ~ IF ~ SPACES ~ disjunction).rep).map {
      case ((isAsync, target), (iter, ifs)) => Comprehension(target, iter, ifs, isAsync)
    }
  )

  def for_if_clauses[ctx: P]: P[Seq[Comprehension]] = P(for_if_clause.rep(1))

  def list_comprehension[ctx: P]: P[ListComp] = P("[" ~ expression ~ SPACES ~ for_if_clauses ~ "]").map { case (e, comps) => ListComp(e, comps) }

  def set_comprehension[ctx: P]: P[SetComp] = P("{" ~ expression ~ SPACES ~ for_if_clauses ~ "}").map { case (e, comps) => SetComp(e, comps) }

  def gen_comprehension[ctx: P]: P[GeneratorExp] = P("(" ~ expression ~ SPACES ~ for_if_clauses ~ ")").map { case (e, comps) => GeneratorExp(e, comps) }

  def dict_kvpair[ctx: P]: P[(PyAST, PyAST)] = P(expression ~ SPACES ~ COLON ~ SPACES ~ expression)

  def dict_comprehension[ctx: P]: P[DictComp] = P("{" ~ dict_kvpair ~ SPACES ~ for_if_clauses ~ "}").map { case (kv, comps) => DictComp(kv._1, kv._2, comps) }

  def list_literal[ctx: P]: P[PyList] = P("[" ~ expressions.? ~ "]").map(elts => PyList(elts.getOrElse(Seq.empty)))

  def tuple_literal[ctx: P]: P[PyTuple] = P("(" ~ expressions.? ~ ")").map(elts => PyTuple(elts.getOrElse(Seq.empty)))

  def dict_element[ctx: P]: P[(Option[PyAST], PyAST)] = P(
    P("**" ~ bitwise_or).map(v => (None, v)) |
      dict_kvpair.map { case (k, v) => (Some(k), v) }
  )

  def dict_literal[ctx: P]: P[PyDict] = P("{" ~ dict_element.rep(sep = P(SPACES ~ COMMA ~ SPACES)) ~ (SPACES ~ COMMA).? ~ "}").map {
    items => PyDict(items.map(_._1), items.map(_._2))
  }

  def primary[ctx: P]: P[PyAST] = P(atom ~ primary_trailer.rep).map {
    case (base, trailers) => trailers.foldLeft(base) {
      case (b, Left(attr)) => Attribute(b, attr)
      case (b, Right(Left(slice))) => Subscript(b, slice)
      case (b, Right(Right((a, kw)))) => Call(b, a, kw)
    }
  }

  def primary_trailer[ctx: P]: P[Either[String, Either[PyAST, (Seq[PyAST], Seq[KeywordArg])]]] = 
    P(P("." ~ NAME).map(Left(_)) | 
      P("[" ~ slices ~ "]").map(s => Right(Left(s))) | 
      P("(" ~ arguments.? ~ ")").map(args => Right(Right(args.getOrElse((Seq.empty, Seq.empty))))))

  def slices[ctx: P]: P[PyAST] = P(
    P(expression.? ~ COLON ~ expression.? ~ (COLON ~ expression.?).?.map(_.flatten)).map { 
      case (lower, upper, step) => Slice(lower, upper, step) 
    } | expression
  )

  def arguments[ctx: P]: P[(Seq[PyAST], Seq[KeywordArg])] = P(
    argument_item.rep(sep = P(SPACES ~ COMMA ~ SPACES)) ~ (SPACES ~ COMMA).?
  ).map { items =>
    val args = items.collect { case Left(ast) => ast }
    val kwargs = items.collect { case Right(kw) => kw }
    (args, kwargs)
  }

  def argument_item[ctx: P]: P[Either[PyAST, KeywordArg]] = P(
    P(NAME ~ SPACES ~ EQUAL ~ SPACES ~ expression).map { case (k, v) => Right(KeywordArg(Some(k), v)) } | 
    P("**" ~ expression).map(v => Right(KeywordArg(None, v))) | 
    expression.map(Left(_))
  )

  def atom[ctx: P]: P[PyAST] = P(
    FALSE.map(_ => Constant("False", "bool")) | 
    TRUE.map(_ => Constant("True", "bool")) | 
    NONE.map(_ => Constant("None", "none")) | 
    dict_comprehension | 
    dict_literal | 
    set_comprehension | 
    list_comprehension | 
    list_literal | 
    gen_comprehension | 
    tuple_literal | 
    STRING.map(s => Constant(s, "str")) | 
    NUMBER.map(n => Constant(n, "int")) | 
    NAME.map(Name(_))
  )

  // ==========================================
  // 5. Atomic Core Statements Configurations
  // ==========================================
  def pyParam[ctx: P]: P[PyParam] = P(
    NAME ~ (SPACES ~ COLON ~ SPACES ~ expression).? ~ (SPACES ~ EQUAL ~ SPACES ~ expression).?
  ).map { case (pName, ann, dft) => PyParam(pName, ann, dft) }

  def params[ctx: P]: P[Seq[PyParam]] = P(pyParam.rep(sep = P(SPACES ~ COMMA ~ SPACES)))

  def import_name[ctx: P]: P[Import] = P(
    IMPORT ~ SPACES ~ dotted_name.rep(1, sep = P(SPACES ~ COMMA ~ SPACES))
  ).map(Import(_))

  def import_from[ctx: P]: P[ImportFrom] = P(
    FROM ~ SPACES ~ P(DOT.rep.! ~ dotted_name.!) ~ SPACES ~ IMPORT ~ SPACES ~ 
    NAME.rep(1, sep = P(SPACES ~ COMMA ~ SPACES))
  ).map { case (dots, mod, names) => ImportFrom(dots + mod, names) }

  def import_stmt[ctx: P]: P[PyAST] = P(import_name | import_from)

  def type_alias[ctx: P]: P[TypeAlias] = P(
    NAME_OR_TYPE ~ SPACES ~ NAME ~ SPACES ~ EQUAL ~ SPACES ~ expression
  ).map { case (alias, target) => TypeAlias(alias, target) }

  def assignment[ctx: P]: P[Assign] = P(
    expression.rep(1, sep = P(SPACES ~ COMMA ~ SPACES)) ~ SPACES ~ EQUAL ~ SPACES ~ expression
  ).map { case (targets, value) => Assign(targets, value) }

  def return_stmt[ctx: P]: P[Return] = P(
    RETURN ~ (SPACES ~ expression).?
  ).map { expr => Return(expr) }

  def raise_stmt[ctx: P]: P[Raise] = P(
    RAISE ~ (SPACES ~ expression).? ~ (SPACES ~ FROM ~ SPACES ~ expression).?
  ).map { case (e, f) => Raise(e, f) }

  def del_stmt[ctx: P]: P[Delete] = P(
    DEL ~ SPACES ~ expression.rep(1, sep = P(SPACES ~ COMMA ~ SPACES))
  ).map { targets => Delete(targets) }

  def assert_stmt[ctx: P]: P[Assert] = P(
    ASSERT ~ SPACES ~ expression ~ (SPACES ~ COMMA ~ SPACES ~ expression).?
  ).map { case (c, m) => Assert(c, m) }

  def pass_stmt[ctx: P]: P[it.evadid.core.parsing.PyAST.Pass] = P(PASS).map(_ => it.evadid.core.parsing.PyAST.Pass())

  def break_stmt[ctx: P]: P[it.evadid.core.parsing.PyAST.Break] = P(BREAK).map(_ => it.evadid.core.parsing.PyAST.Break())

  def continue_stmt[ctx: P]: P[it.evadid.core.parsing.PyAST.Continue] = P(CONTINUE).map(_ => it.evadid.core.parsing.PyAST.Continue())

  def simple_stmt[ctx: P]: P[PyAST] = P(
    type_alias | assignment | import_stmt | return_stmt | raise_stmt | del_stmt | 
    assert_stmt | pass_stmt | break_stmt | continue_stmt | expression.map(ExprStmt(_))
  )

  def simple_stmts[ctx: P]: P[Seq[PyAST]] = P(
    simple_stmt.rep(1, sep = P(SPACES ~ SEMI ~ SPACES)) ~ (SPACES ~ SEMI).? ~ NEWLINE
  ).map(_.toSeq)

  // ==========================================
  // 6. Complete Control-Flow & Compound Structures
  // ==========================================
  
  // --- IF / ELIF / ELSE ---
  def ifStmt[ctx: P](state: IndentState): P[If] = P(
    IF ~ SPACES ~ expression ~ SPACES ~ COLON ~ NEWLINE ~ 
    executionBlock(state.currentLevel + 4) ~ elifBranches(state)
  ).map { case (expr, body, orelse) => If(expr, body, orelse) }

  def elifBranches[ctx: P](state: IndentState): P[Seq[PyAST]] = {
    P(
      (P(" ".rep(state.currentLevel).!) ~ ELIF ~ SPACES ~ expression ~ SPACES ~ COLON ~ NEWLINE ~ 
       executionBlock(state.currentLevel + 4) ~ elifBranches(state)).map { 
        case ((indent, expr), (body, orelse)) => Seq(If(expr, body, orelse)) 
      } |
      (P(" ".rep(state.currentLevel).!) ~ ELSE ~ SPACES ~ COLON ~ NEWLINE ~ 
       executionBlock(state.currentLevel + 4)).?.map(_.getOrElse(Seq.empty))
    )
  }

  // --- WHILE Loops ---
  def whileStmt[ctx: P](state: IndentState): P[While] = P(
    WHILE ~ SPACES ~ expression ~ SPACES ~ COLON ~ NEWLINE ~ 
    executionBlock(state.currentLevel + 4) ~ elseBlock(state)
  ).map { case (expr, body, orelse) => While(expr, body, orelse) }

  // --- FOR Loops ---
  def forStmt[ctx: P](state: IndentState, isAsync: Boolean = false): P[For] = P(
    FOR ~ SPACES ~ expression ~ SPACES ~ IN ~ SPACES ~ expression ~ SPACES ~ COLON ~ NEWLINE ~ 
    executionBlock(state.currentLevel + 4) ~ elseBlock(state)
  ).map { case (expr1, expr2, body, orelse) => For(expr1, expr2, body, orelse, isAsync) }

  def asyncForStmt[ctx: P](state: IndentState): P[For] = P(ASYNC ~ SPACES ~ forStmt(state, isAsync = true))

  def elseBlock[ctx: P](state: IndentState): P[Seq[PyAST]] = {
    P((P(" ".rep(state.currentLevel).!) ~ ELSE ~ SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)).?.map {
      case Some((indent, body)) => body
      case None => Seq.empty
    })
  }

  def withItem[ctx: P]: P[WithItem] = P(
    expression ~ (SPACES ~ AS ~ SPACES ~ expression).?
  ).map { case (e, t) => WithItem(e, t) }

  def withStmt[ctx: P](state: IndentState, isAsync: Boolean = false): P[With] = P(
    WITH ~ SPACES ~ withItem.rep(1, sep = P(SPACES ~ COMMA ~ SPACES)) ~ SPACES ~ COLON ~ NEWLINE ~ 
    executionBlock(state.currentLevel + 4)
  ).map { case (items, body) => With(items, body, isAsync) }

  def asyncWithStmt[ctx: P](state: IndentState): P[With] = P(ASYNC ~ SPACES ~ withStmt(state, isAsync = true))

  // --- TRY / EXCEPT / FINALLY Error Blocks ---
  def exceptHandler[ctx: P](state: IndentState): P[ExceptHandler] = {
    P(
      P(" ".rep(state.currentLevel).!) ~ EXCEPT ~ 
      (SPACES ~ STAR.!.map(_ => true) | SPACES.map(_ => false)) ~ 
      (SPACES ~ expression).? ~ (SPACES ~ AS ~ SPACES ~ NAME).? ~ 
      SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)
    ).map { 
      case (indent, isStar, tExpr, target, body) => 
        ExceptHandler(tExpr.map(_.asInstanceOf[PyAST]), target.map(_.asInstanceOf[String]), body, isStar)
    }
  }

  def tryStmt[ctx: P](state: IndentState): P[Try] = P(
    TRY ~ SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4) ~ 
    exceptHandler(state).rep ~ elseBlock(state) ~ finallyBlock(state)
  ).map { case (body, handlers, orelse, finalBody) => Try(body, handlers, orelse, finalBody) }

  def finallyBlock[ctx: P](state: IndentState): P[Seq[PyAST]] = {
    P((P(" ".rep(state.currentLevel).!) ~ FINALLY ~ SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)).?.map {
      case Some((indent, body)) => body
      case None => Seq.empty
    })
  }

  // --- MATCH STATEMENTS (PEP 634) ---
  def matchStmt[ctx: P](state: IndentState): P[MatchStatement] = {
    P(
      NAME_OR_MATCH ~ SPACES ~ expression ~ SPACES ~ COLON ~ NEWLINE ~ 
      matchBodyBlock(state)
    ).map { case (matchKeyword, subject, cases) => MatchStatement(subject, cases) }
  }

  def matchBodyBlock[ctx: P](state: IndentState): P[Seq[MatchCase]] = {
    P(matchCaseRule(state).rep).map(_.flatten)
  }

  def matchCaseRule[ctx: P](state: IndentState): P[MatchCase] = {
    P(
      P(" ".rep(state.currentLevel).!) ~ NAME_OR_CASE ~ SPACES ~ 
      (pattern | expression).? ~ (SPACES ~ IF ~ SPACES ~ expression).? ~ 
      SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)
    ).map { 
      case ((indent, _), (pattern, guard), body) => 
        MatchCase(pattern.getOrElse(Constant("True", "bool")), guard, body)
    }
  }

  def pattern[ctx: P]: P[PyAST] = P(
    NAME.map(Name(_)) | 
    literal_pattern | 
    sequence_pattern | 
    mapping_pattern | 
    class_pattern
  )

  def literal_pattern[ctx: P]: P[PyAST] = P(
    FALSE.map(_ => Constant("False", "bool")) | 
    TRUE.map(_ => Constant("True", "bool")) | 
    NONE.map(_ => Constant("None", "none")) | 
    STRING.map(s => Constant(s, "str")) | 
    NUMBER.map(n => Constant(n, "int"))
  )

  def sequence_pattern[ctx: P]: P[PyAST] = P(
    P("[" ~ pattern.rep(sep = P(SPACES ~ COMMA ~ SPACES)) ~ "]") |
    P("(" ~ pattern.rep(sep = P(SPACES ~ COMMA ~ SPACES)) ~ ")")
  ).map { case (elts) => PyList(elts) }

  def mapping_pattern[ctx: P]: P[PyAST] = P(
    P("{" ~ (pattern ~ SPACES ~ COLON ~ SPACES ~ pattern).rep(sep = P(SPACES ~ COMMA ~ SPACES)) ~ 
       (SPACES ~ COMMA).? ~ "}") |
    P("{" ~ "**" ~ NAME ~ SPACES ~ "}")
  ).map { items => 
    items match {
      case items: Seq[_] => PyDict(items.asInstanceOf[Seq[(PyAST, PyAST)]].map { case (k, v) => (Some(k), v) }, items.asInstanceOf[Seq[(PyAST, PyAST)]].map(_._2))
      case _ => PyDict(Seq.empty, Seq.empty)
    }
  }

  def class_pattern[ctx: P]: P[PyAST] = P(
    dotted_name ~ SPACES ~ LPAR ~ 
    (pattern.rep(sep = P(SPACES ~ COMMA ~ SPACES)) | 
     (keyword_pattern.rep(sep = P(SPACES ~ COMMA ~ SPACES)))).? ~ 
    RPAR
  ).map { case (className, args) => Call(Name(className), args.getOrElse(Seq.empty), Seq.empty) }

  def keyword_pattern[ctx: P]: P[(String, PyAST)] = P(
    NAME ~ SPACES ~ EQUAL ~ SPACES ~ pattern
  ).map { case (name, pattern) => (name, pattern) }

  // ==========================================
  // 7. Base Block Code Resolution Layer
  // ==========================================
  def function_def_raw[ctx: P](state: IndentState, isAsync: Boolean, decs: Seq[PyAST]): P[FunctionDef] = P(
    DEF ~ SPACES ~ NAME ~ SPACES ~ LPAR ~ params ~ RPAR ~ 
    (SPACES ~ RARROW ~ SPACES ~ expression).? ~ 
    SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)
  ).map { case (name, params, ret, body) => 
    FunctionDef(name, params, ret.map(_.asInstanceOf[PyAST]), body, isAsync, decs) 
  }

  def function_def[ctx: P](state: IndentState, decs: Seq[PyAST]): P[FunctionDef] = P(
    function_def_raw(state, isAsync = false, decs) | 
    (ASYNC ~ SPACES ~ function_def_raw(state, isAsync = true, decs))
  )

  def class_def_raw[ctx: P](state: IndentState, decs: Seq[PyAST]): P[ClassDef] = P(
    CLASS ~ SPACES ~ NAME ~ 
    (SPACES ~ LPAR ~ expression.rep(sep = ",") ~ RPAR).?.map(_.getOrElse(Seq.empty)) ~ 
    SPACES ~ COLON ~ NEWLINE ~ executionBlock(state.currentLevel + 4)
  ).map { case (name, bases, body) => ClassDef(name, bases, body, decs) }

  def compound_stmt[ctx: P](state: IndentState): P[PyAST] = P(
    function_def(state, Seq.empty) |
    class_def(state, Seq.empty) |
    ifStmt(state) |
    whileStmt(state) |
    forStmt(state) |
    asyncForStmt(state) |
    withStmt(state) |
    asyncWithStmt(state) |
    tryStmt(state) |
    matchStmt(state) |
    type_alias.map(ExprStmt(_)) |
    assignment.map(ExprStmt(_))
  )

  def class_def[ctx: P](state: IndentState, decs: Seq[PyAST]): P[ClassDef] = P(
    class_def_raw(state, decs) | 
    (ASYNC ~ SPACES ~ class_def_raw(state, decs))
  )

  def statement[ctx: P](state: IndentState): P[Seq[PyAST]] = P(
    compound_stmt(state).map(Seq(_)) | simple_stmts
  )

  def executionBlock[ctx: P](indentNeeded: Int): P[Seq[PyAST]] = {
    val innerState = IndentState(indentNeeded)
    P((P(" ".rep(indentNeeded).!) ~ statement(innerState) ~ NEWLINE.?).map { 
      case (indent, stmt, nl) => stmt 
    }).rep(1).map(_.flatten.toSeq)
  }

  def file_input[ctx: P]: P[Module] = P(
    SPACES ~ statement(IndentState(0)).rep ~ NEWLINE.?
  ).map { statements => Module(statements.flatten) }

  def parse(pythonCode: String): Either[String, Module] = {
    fastparse.parse(pythonCode, c => file_input(using c)) match {
      case Parsed.Success(astModule, _) => Right(astModule)
      case f: Parsed.Failure => Left(s"Python Parsing Error: ${f.trace().longAggregateMsg}")
    }
  }
}
