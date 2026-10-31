package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.parsing.generic.abstractions.pipeline.GenericAstScanner
import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.JavaLexer.*
import it.evadid.vm.parsing.generic.CodeLexer.{LPAR, RPAR, LSQB, RSQB, LBRACE, RBRACE, DOT, COMMA, SEMI, LESS, GREATER, ASSIGN}
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.parsing.java.clean.model.{JavaAST, JavaType}
import it.evadid.vm.parsing.java.clean.JavaSourceMap.{Node, SourceRange}

object JavaParser extends JavaParser(retainParentheses = false)

class JavaParser(private val retainParentheses: Boolean) extends GenericAstScanner[JavaAST] {
  private val grammar = new JavaGrammar(retainParentheses, trackLocations = false)
  private val locatedGrammar = new JavaGrammar(retainParentheses, trackLocations = true)

  override def parseASTFromProgramString(programString: String): Either[Throwable, JavaAST] = parse(programString)

  def parse(javaCode: String): Either[Throwable, JavaProgram] = {
    val trimmed = javaCode.trim
    fastparse.parse(trimmed, c => javaProgram(using c)) match {
      case Parsed.Success(program, _) => Right(program)
      case f: Parsed.Failure =>
        val (line, column) = ProgrammingConversionLocation.inTrimmed(javaCode, trimmed, f.index)
        Left(ProgrammingConversionException.unparsable(
          s"Java Parsing Error: ${f.trace().longAggregateMsg}", Some(line), Some(column)))
    }
  }

  def parseWithLocations(source: String): Either[Parsed.Failure, JavaSourceMap] =
    fastparse.parse(source, c => locatedGrammar.javaProgram(using c)) match {
      case Parsed.Success(root, _) => Right(new JavaSourceMap(source, root))
      case failure: Parsed.Failure => Left(failure)
    }

  def javaProgram[$: P]: P[JavaProgram] = grammar.javaProgram.map(_.value)
}

private class JavaGrammar(retainParentheses: Boolean, trackLocations: Boolean) {
  private val emptyRange = SourceRange(0, 0)

  private def captured[A, $: P](rule: => P[A]): P[(SourceRange, A)] =
    if trackLocations then P(Index ~ rule ~ Index).map { (start, value, end) => (SourceRange(start, end), value) }
    else rule.map(value => (emptyRange, value))

  private def node[A <: JavaAST](value: A, range: SourceRange,
      children: => Seq[Node[JavaAST]] = Seq.empty): Node[A] =
    Node(value, range, if trackLocations then children.toVector else Vector.empty)

  private def leaf[A <: JavaAST, $: P](rule: => P[A]): P[Node[A]] =
    captured(rule).map { (range, value) => node(value, range) }

  private def joined(left: Node[?], right: SourceRange): SourceRange = SourceRange(left.range.start, right.end)

  private def withRange[A <: JavaAST](value: Node[A], range: SourceRange): Node[A] =
    if trackLocations then value.copy(range = range) else value

  // ==========================================
  // 1. HELPER
  // ==========================================

  private def ws[$: P]: P[Unit] = P((WS | LINE_COMMENT | BLOCK_COMMENT).rep)
  private def ws1[$: P]: P[Unit] = P((WS | LINE_COMMENT | BLOCK_COMMENT).rep(1))

  // ==========================================
  // 2. PROGRAM
  // ==========================================

  def javaProgram[$: P]: P[Node[JavaProgram]] = captured(P(ws ~ statement.rep ~ ws ~ End)).map { (range, statements) =>
    val wrapped = statements.zipWithIndex.map { (statement, index) =>
      node(StatementWithLineNumber(statement.value, index), statement.range, Seq(statement))
    }
    node(JavaProgram(wrapped.map(_.value)), range, wrapped)
  }

  // ==========================================
  // 3. SEQUENCES OF STATEMENTS OR EXPRESSIONS
  // ==========================================

  def block[$: P]: P[Node[JavaExecutionBlock]] = captured(P(LBRACE ~ ws ~ statement.rep ~ ws ~ RBRACE)).map {
    (range, statements) => node(JavaExecutionBlock(statements.map(_.value)), range, statements)
  }

  def arguments[$: P]: P[Seq[Node[JavaExpression]]] = P(expression.rep(sep = ws ~ COMMA ~ ws))

  def parameters[$: P]: P[Seq[Node[JavaVariableDeclaration]]] = P(parameter.rep(sep = ws ~ COMMA ~ ws))

  // ==========================================
  // 4. STATEMENTS TYPES
  // ==========================================

  def statement[$: P]: P[Node[JavaStatement]] = P(ws ~ (compoundStatement | simpleStatement | unparsable) ~ ws)

  def simpleStatement[$: P]: P[Node[JavaStatement]] = P(
    packageStatement | importStatement | returnStatement | throwStatement | breakStatement | continueStatement |
      variableDeclaration | assignment | expressionStatement | emptyStatement
  )

  def compoundStatement[$: P]: P[Node[JavaStatement]] = P(classDef | methodDef | ifStatement | whileStatement | forStatement | tryStatement)

  def emptyStatement[$: P]: P[Node[JavaStatement]] = leaf(P(SEMI).map(_ => JavaEmptyStatement))

  def expressionStatement[$: P]: P[Node[JavaStatement]] =
    captured(P(expression ~ ws ~ SEMI)).map { (range, value) => withRange(value, range) }

  def unparsable[$: P]: P[Node[JavaUnparsableStatement]] =
    leaf(P((!SEMI ~ !RBRACE ~ AnyChar).rep(1).! ~ SEMI.?).map(JavaUnparsableStatement(_)))

  // ==========================================
  // 5. INDIVIDUAL STATEMENTS
  // ==========================================

  def packageStatement[$: P]: P[Node[JavaPackageStatement]] =
    leaf(P(keyword("package") ~ ws1 ~ qualifiedName ~ ws ~ SEMI).map(JavaPackageStatement(_)))

  def importStatement[$: P]: P[Node[JavaImportStatement]] =
    leaf(P(keyword("import") ~ ws1 ~ (keyword("static").!.map(_ => "static") ~ ws1).? ~ qualifiedName ~ (DOT ~ "*".!).? ~ ws ~ SEMI).map {
      case (isStatic, name, all) => JavaImportStatement(name, isStatic.nonEmpty, all.nonEmpty)
    })

  def returnStatement[$: P]: P[Node[JavaReturnStatement]] =
    captured(P(keyword("return") ~ (ws1 ~ expression).? ~ ws ~ SEMI)).map { (range, value) =>
      node(JavaReturnStatement(value.map(_.value)), range, value.toSeq)
    }

  def throwStatement[$: P]: P[Node[JavaThrowStatement]] =
    captured(P(keyword("throw") ~ ws1 ~ expression ~ ws ~ SEMI)).map { (range, value) =>
      node(JavaThrowStatement(value.value), range, Seq(value))
    }

  def breakStatement[$: P]: P[Node[JavaStatement]] = leaf(P(keyword("break") ~ ws ~ SEMI).map(_ => JavaBreakStatement))

  def continueStatement[$: P]: P[Node[JavaStatement]] = leaf(P(keyword("continue") ~ ws ~ SEMI).map(_ => JavaContinueStatement))

  def ifStatement[$: P]: P[Node[JavaIfStatement]] =
    captured(P(keyword("if") ~ ws ~ LPAR ~ ws ~ expression ~ ws ~ RPAR ~ ws ~ block ~ (ws ~ keyword("else") ~ ws ~ (block | ifStatement.map(stmt => node(JavaExecutionBlock(Seq(stmt.value)), stmt.range, Seq(stmt))))).?)).map {
      case (range, (cond, thenBlock, elseBlock)) =>
        node(JavaIfStatement(cond.value, thenBlock.value, elseBlock.map(_.value)), range, Seq(cond, thenBlock) ++ elseBlock)
    }

  def whileStatement[$: P]: P[Node[JavaWhileStatement]] =
    captured(P(keyword("while") ~ ws ~ LPAR ~ ws ~ expression ~ ws ~ RPAR ~ ws ~ block)).map {
      case (range, (cond, body)) => node(JavaWhileStatement(cond.value, body.value), range, Seq(cond, body))
    }

  def forStatement[$: P]: P[Node[JavaForStatement]] =
    captured(P(keyword("for") ~ ws ~ LPAR ~ ws ~ forInit.? ~ ws ~ SEMI ~ ws ~ expression.? ~ ws ~ SEMI ~ ws ~ expression.rep(sep = ws ~ COMMA ~ ws) ~ ws ~ RPAR ~ ws ~ block)).map {
      case (range, (init, cond, update, body)) =>
        val initial = init.getOrElse(Seq.empty)
        node(JavaForStatement(initial.map(_.value), cond.map(_.value), update.map(_.value), body.value),
          range, initial ++ cond ++ update ++ Seq(body))
    }

  def tryStatement[$: P]: P[Node[JavaTryStatement]] =
    captured(P(keyword("try") ~ ws ~ block ~ catchClause.rep ~ (ws ~ keyword("finally") ~ ws ~ block).?)).map {
      case (range, (body, catches, last)) =>
        node(JavaTryStatement(body.value, catches.map(_.value), last.map(_.value)), range, Seq(body) ++ catches ++ last)
    }

  def catchClause[$: P]: P[Node[JavaCatchClause]] =
    P(ws ~ captured(P(keyword("catch") ~ ws ~ LPAR ~ ws ~ parameter ~ ws ~ RPAR ~ ws ~ block))).map {
      case (range, (parameter, body)) => node(JavaCatchClause(parameter.value, body.value), range, Seq(parameter, body))
    }

  // Assignments and declarations

  def modifiers[$: P]: P[Seq[String]] = P((modifier ~ ws1).rep)

  def variableDeclaration[$: P]: P[Node[JavaVariableDeclaration]] =
    captured(P(variableDeclarationNoSemi ~ ws ~ SEMI)).map { (range, value) => withRange(value, range) }

  def variableDeclarationNoSemi[$: P]: P[Node[JavaVariableDeclaration]] =
    captured(P(modifiers ~ javaType ~ ws ~ NAME ~ (ws ~ ASSIGN ~ ws ~ expression).?)).map {
      case (range, (mods, typ, name, value)) => node(JavaVariableDeclaration(name, typ, value.map(_.value), mods), range, value.toSeq)
    }

  def parameter[$: P]: P[Node[JavaVariableDeclaration]] =
    captured(P(modifiers ~ javaType ~ ws ~ NAME)).map {
      case (range, (mods, typ, name)) => node(JavaVariableDeclaration(name, typ, None, mods), range)
    }

  def assignment[$: P]: P[Node[JavaStatement]] =
    captured(P(assignmentNoSemi ~ ws ~ SEMI)).map { (range, value) => withRange(value, range) }

  def assignmentNoSemi[$: P]: P[Node[JavaStatement]] =
    captured(P(target ~ ws ~ (operator("+=", "-=", "*=", "/=", "%=") | ASSIGN.!) ~ ws ~ expression)).map {
      case (range, (target, "=", value)) => node(JavaAssignment(target.value, value.value), range, Seq(target, value))
      case (range, (target, op, value)) => node(JavaAugAssignment(target.value, op, value.value), range, Seq(target, value))
    }

  // Definitions

  def methodDef[$: P]: P[Node[JavaMethodDef]] =
    captured(P(modifiers ~ javaType.? ~ ws ~ NAME ~ ws ~ LPAR ~ ws ~ parameters.? ~ ws ~ RPAR ~ ws ~ block)).map {
      case (range, (mods, returnType, name, params, body)) =>
        val parameters = params.getOrElse(Seq.empty)
        node(JavaMethodDef(name, mods, returnType, parameters.map(_.value), body.value), range, parameters :+ body)
    }

  def classDef[$: P]: P[Node[JavaClassDef]] =
    captured(P(modifiers ~ keyword("class") ~ ws1 ~ NAME ~ (ws1 ~ keyword("extends") ~ ws1 ~ javaType).? ~ (ws1 ~ keyword("implements") ~ ws1 ~ javaType.rep(1, sep = ws ~ COMMA ~ ws)).? ~ ws ~ block)).map {
      case (range, (mods, name, ext, impl, body)) =>
        node(JavaClassDef(name, mods, ext, impl.getOrElse(Seq.empty), body.value), range, Seq(body))
    }

  private def forInit[$: P]: P[Seq[Node[JavaStatement]]] =
    P(variableDeclarationNoSemi.map(Seq(_)) | (assignmentNoSemi | expression).rep(1, sep = ws ~ COMMA ~ ws))

  // ==========================================
  // 7. EXPRESSION TYPES
  // ==========================================

  def expression[$: P]: P[Node[JavaExpression]] = P(assignmentExpression | logicalOr)

  def assignmentExpression[$: P]: P[Node[JavaExpression]] =
    captured(P(target ~ ws ~ operator("+=", "-=", "*=", "/=", "%=", "=") ~ ws ~ expression)).map {
      case (range, (target, op, value)) => node(JavaAssignmentExpression(target.value, op, value.value), range, Seq(target, value))
    }

  // ==========================================
  // 8. INDIVIDUAL EXPRESSIONS
  // ==========================================

  def logicalOr[$: P]: P[Node[JavaExpression]] = binary(logicalAnd, "||")
  def logicalAnd[$: P]: P[Node[JavaExpression]] = binary(equality, "&&")
  def equality[$: P]: P[Node[JavaExpression]] = binary(comparison, "==", "!=")
  def comparison[$: P]: P[Node[JavaExpression]] = binary(sum, "<=", ">=", "<", ">")
  def sum[$: P]: P[Node[JavaExpression]] = binary(term, "+", "-")
  def term[$: P]: P[Node[JavaExpression]] = binary(factor, "*", "/", "%")
  def factor[$: P]: P[Node[JavaExpression]] =
    captured(P(operator("++", "--") ~ ws ~ factor)).map { case (range, (operator, operand)) =>
      node(JavaUpdateExpression(operand.value, operator, prefix = true), range, Seq(operand))
    } | captured(P(operator("!", "-", "+") ~ ws ~ factor)).map { case (range, (op, operand)) =>
      node(JavaOperationUnary(op, operand.value), range, Seq(operand))
    } | postfix

  private def postfix[$: P]: P[Node[JavaExpression]] =
    P(primary ~ (ws ~ captured(operator("++", "--"))).rep).map { (operand, operators) =>
      operators.foldLeft(operand) { case (expression, (range, operator)) =>
        node(JavaUpdateExpression(expression.value, operator, prefix = false), joined(expression, range), Seq(expression))
      }
    }

  private def binary[$: P](next: => P[Node[JavaExpression]], ops: String*): P[Node[JavaExpression]] =
    P(next ~ (ws ~ operator(ops*) ~ ws ~ next).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) =>
        node(JavaOperationBinary(left.value, op, right.value), joined(left, right.range), Seq(left, right))
      }
    }

  // ==========================================
  // 8. ATOMAR SEQUENCES
  // ==========================================

  def primary[$: P]: P[Node[JavaExpression]] = P(atom ~ trailer.rep).map { case (base, trailers) =>
    trailers.foldLeft(base) { case (receiver, applyTrailer) => applyTrailer(receiver) }
  }

  def atom[$: P]: P[Node[JavaExpression]] = P(newExpression | literal | parenthesized | targetAtom)

  def parenthesized[$: P]: P[Node[JavaExpression]] =
    captured(P(LPAR ~ ws ~ expression ~ ws ~ RPAR)).map { (range, expression) =>
      if retainParentheses then node(JavaParenthesizedExpression(expression.value), range, Seq(expression))
      else withRange(expression, range)
    }

  private def trailer[$: P]: P[Node[JavaExpression] => Node[JavaExpression]] = P(attributeTrailer | callTrailer | subscriptTrailer)

  private def attributeTrailer[$: P]: P[Node[JavaExpression] => Node[JavaExpression]] =
    captured(P(ws ~ DOT ~ ws ~ NAME)).map { (range, name) => receiver =>
      node(JavaAttributeAccess(receiver.value, name), joined(receiver, range), Seq(receiver))
    }

  private def callTrailer[$: P]: P[Node[JavaExpression] => Node[JavaExpression]] =
    captured(P(ws ~ LPAR ~ ws ~ arguments.? ~ ws ~ RPAR)).map { (range, args) => receiver =>
      val arguments = args.getOrElse(Seq.empty)
      val value = receiver.value match {
        case target: JavaTarget => JavaFunctionCall(target, arguments.map(_.value))
        case callee => JavaCallExpression(callee, arguments.map(_.value))
      }
      node(value, joined(receiver, range), receiver +: arguments)
    }

  private def subscriptTrailer[$: P]: P[Node[JavaExpression] => Node[JavaExpression]] =
    captured(P(ws ~ LSQB ~ ws ~ expression ~ ws ~ RSQB)).map { (range, index) => receiver =>
      node(JavaSubscript(receiver.value, Seq(index.value)), joined(receiver, range), Seq(receiver, index))
    }

  private def targetAtom[$: P]: P[Node[JavaTarget]] = leaf(NAME.map(JavaTarget(_)))

  def functionCall[$: P]: P[Node[JavaExpression]] = captured(P(target ~ ws ~ LPAR ~ ws ~ arguments.? ~ ws ~ RPAR)).map {
    case (range, (func, args)) =>
      val arguments = args.getOrElse(Seq.empty)
      node(JavaFunctionCall(func.value, arguments.map(_.value)), range, func +: arguments)
  }

  def newExpression[$: P]: P[Node[JavaExpression]] =
    captured(P(keyword("new") ~ ws1 ~ javaType ~ ws ~ LPAR ~ ws ~ arguments.? ~ ws ~ RPAR)).map {
      case (range, (typ, args)) =>
        val arguments = args.getOrElse(Seq.empty)
        node(JavaNewExpression(typ, arguments.map(_.value)), range, arguments)
    }

  def target[$: P]: P[Node[JavaTarget]] = captured(P(qualifiedName ~ (ws ~ LSQB ~ ws ~ expression ~ ws ~ RSQB).?)).map {
    case (range, (name, slice)) =>
      val parts = name.split('.').toSeq
      node(JavaTarget(parts.last, parts.dropRight(1), slice.map(_.value)), range, slice.toSeq)
  }

  // ==========================================
  // 9. Value Literals
  // ==========================================

  def literal[$: P]: P[Node[JavaLiteral[?]]] = leaf(P(
    STRING_LITERAL.map(JAVA_STRING().createLiteralUnsafe) |
      NUMBER_LITERAL.map(raw => if (raw.contains('.')) JAVA_FLOAT().createLiteralUnsafe(raw) else JAVA_INTEGER().createLiteralUnsafe(raw)) |
      P(keyword("true").!.map(_ => "true") | keyword("false").!.map(_ => "false")).map(JAVA_BOOL().createLiteralUnsafe) |
      P(keyword("null").!).map(raw => JAVA_UNPARSABLE_TYPE(raw).createLiteralUnsafe(raw): JavaLiteral[?])
  ))

  // ==========================================
  // 10. Type Literals
  // ==========================================

  def javaType[$: P]: P[JavaType[?]] = P(parameterizedOrBaseType ~ (ws ~ "[".! ~ ws ~ "]").rep).map { case (base, arraySuffixes: Seq[String]) =>
    arraySuffixes.foldLeft(base) { case (elementType, _) => JAVA_ARRAY(elementType.asInstanceOf[JavaType[Any]]) }
  }

  private def parameterizedOrBaseType[$: P]: P[JavaType[?]] = P(listType | baseJavaType)

  private def listType[$: P]: P[JavaType[?]] =
    P(("List" | "java.util.List") ~ ws ~ LESS ~ ws ~ javaType ~ ws ~ GREATER).map(elementType => JAVA_LIST(elementType.asInstanceOf[JavaType[Any]]))

  private def baseJavaType[$: P]: P[JavaType[?]] = P(
    keyword("boolean").map(_ => JAVA_BOOL()) |
      keyword("byte").map(_ => JAVA_INTEGER()) |
      keyword("short").map(_ => JAVA_INTEGER()) |
      keyword("int").map(_ => JAVA_INTEGER()) |
      keyword("long").map(_ => JAVA_INTEGER()) |
      keyword("float").map(_ => JAVA_FLOAT()) |
      keyword("double").map(_ => JAVA_FLOAT()) |
      keyword("String").map(_ => JAVA_STRING()) |
      keyword("void").map(_ => JAVA_UNPARSABLE_TYPE("void")) |
      qualifiedName.map(JAVA_UNPARSABLE_TYPE(_))
  )
}
