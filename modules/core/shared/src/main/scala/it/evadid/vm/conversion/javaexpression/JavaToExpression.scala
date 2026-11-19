package it.evadid.vm.conversion.javaexpression

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.compilation.java.turtle.{JavaTurtleCompiler, JavaTurtleInputLimits, JavaTurtleProgram, JavaTurtleSourceParser}
import it.evadid.vm.conversion.parsed.{ParsedExpression, ParsedFunctionKind, ParsedParameter, ParsedProgram, ParsedProgramBuilder, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression

/** Java front end: fastparse syntax tree → [[ParsedProgram]] → shared expression tree.
  *
  * The class declaring a static `main` is the module: its static fields and methods are module
  * variables and functions, and `main`'s body is the main sequence. Classes without an entry point are
  * object classes. Loose statements and methods outside any class are module code as well.
  */
final class JavaToExpression(code: String) {
  private lazy val parsed: JavaProgram = {
    JavaTurtleInputLimits.checkParserInput(code).fold(
      diagnostic => rejectAt(diagnostic.message, code, diagnostic.range.map(_.start)), identity)
    JavaToExpression.parser.parse(code).fold(
      {
        case conversion: ProgrammingConversionException =>
          throw ProgrammingConversionException.unparsable(
            "This Java source cannot be read as a complete program or fragment.", conversion.error.line, conversion.error.column)
        case error => throw IllegalArgumentException("This Java source cannot be read as a complete program or fragment.", error)
      },
      program => { rejectUnparsable(program); program })
  }

  private def rejectAt(message: String, source: String, offset: Option[Int]): Nothing = {
    val (line, column) = offset.map(ProgrammingConversionLocation.lineColumn(source, _)).map((l, c) => (Some(l), Some(c))).getOrElse((None, None))
    throw ProgrammingConversionException.unparsable(message, line, column)
  }

  private def rejectUnparsable(program: JavaProgram): Unit = {
    def visit(node: it.evadid.vm.parsing.generic.abstractions.GenericAST): Unit = node match {
      case statement: JavaUnparsableStatement =>
        val index = code.indexOf(statement.source)
        throw ProgrammingConversionException.unparsable("This Java source contains unsupported or unfinished syntax.",
          Option.when(index >= 0)(ProgrammingConversionLocation.lineColumn(code, index)._1))
      case other => other.getChildren().foreach(visit)
    }
    visit(program)
  }

  def isClassProgram: Boolean = parsed.statements.exists(_.statement.isInstanceOf[JavaClassDef])

  def toLegacyTurtleCommands: List[TurtleCommand[Double]] = {
    if isClassProgram then throw IllegalArgumentException("Full Java classes require the checked Java runner.")
    convert.turtleCommandExecutionInfo.completedCommands
  }

  def compileForExecution: Either[JavaTurtleSourceParser.Diagnostic, JavaTurtleProgram] = JavaTurtleCompiler.compile(code)

  lazy val preparedSource = it.evadid.vm.conversion.structured.PreparedProgramSource(code, Java)

  /** Fails, rather than keeping placeholders, when part of the program has no shared representation. */
  def convert: ProgrammingStateBeExpression = {
    val expression = BeStartProgram(JavaToExpression.builder.build(JavaAstToParsed(parsed)).expression)
    val nodes = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
    nodes.collectFirst {
      case BeExpressionReference(_, BeExpressionUnsupported(source, _)) => source
      case BeExpressionReference(_, BeExpressionUnparsable(source, _, _)) => source
    }.foreach(source => throw ProgrammingConversionException.unsupported(
      s"This Java construct is not supported by the shared expression tree: ${source.take(160)}"))
    ProgrammingStateBeExpression(expression)
  }
}

object JavaToExpression {
  private val parser = new JavaParser(retainParentheses = false, retainComments = true)
  private val builder = new ParsedProgramBuilder(Java)
}

/** Translates Java syntax into the language-neutral [[ParsedProgram]]. */
private object JavaAstToParsed {
  private final case class Owner(name: String, fields: Set[String], methods: Map[String, Boolean], isStatic: Boolean)
  /** `module` names the entry class, whose qualifier is dropped from calls and field accesses. */
  private final case class Scope(owner: Option[Owner], locals: Set[String], module: Option[String]) {
    def withLocal(name: String): Scope = copy(locals = locals + name)
  }

  def apply(program: JavaProgram): ParsedProgram = {
    val topLevel = program.statements.map(_.statement)
    val classes = topLevel.collect { case definition: JavaClassDef => definition }
    val entry = classes.find(definition => definition.body.statements.exists {
      case method: JavaMethodDef => isEntryPoint(method)
      case _ => false
    })
    val nested = classes.flatMap(definition => definition.body.statements.collect { case inner: JavaClassDef => inner })
    val objectClasses = (classes.filterNot(entry.contains) ++ nested).map(classDef)
    val moduleName = entry.map(_.name).orElse(Option.when(!classes.exists(_.name == "Main"))("Main"))
    val moduleScope = Scope(None, Set.empty, moduleName)
    val loose = statements(topLevel.filterNot(_.isInstanceOf[JavaClassDef]), moduleScope)
    ParsedProgram(objectClasses ++ loose ++ entry.toSeq.flatMap(module(_, moduleScope)))
  }

  private def isEntryPoint(method: JavaMethodDef): Boolean =
    method.name == "main" && method.modifiers.contains("static") && method.parameters.size <= 1 &&
      method.parameters.forall(_.javaType.isInstanceOf[JAVA_ARRAY[?]])

  private def module(definition: JavaClassDef, scope: Scope): Seq[ParsedStatement] = {
    val members = definition.body.statements.filterNot(member => member.isInstanceOf[JavaCommentStatement] || member.isInstanceOf[JavaClassDef] || member == JavaEmptyStatement)
    val isStaticMember: JavaStatement => Boolean = {
      case field: JavaVariableDeclaration => field.modifiers.contains("static")
      case method: JavaMethodDef => method.modifiers.contains("static")
      case _ => true
    }
    if members.exists(isStaticMember) && !members.forall(isStaticMember) then
      throw ProgrammingConversionException.unsupported("Mixed static and instance members are not supported by the shared expression tree.")
    val main = members.collectFirst { case method: JavaMethodDef if isEntryPoint(method) => method }.get
    main.parameters.headOption.foreach { arguments =>
      if mentions(main.body, arguments.name) then
        throw ProgrammingConversionException.unsupported("Program arguments cannot be represented by the shared startup expression.")
    }
    val declarations = members.filterNot(_ eq main).flatMap {
      case field: JavaVariableDeclaration =>
        Seq(field.value.fold[ParsedStatement](S.Declare(field.name, parsedType(field.javaType)))(value =>
          S.Assign(field.name, Some(parsedType(field.javaType)), expression(value, scope))))
      case method: JavaMethodDef => Seq(function(method, ParsedFunctionKind.Function, scope))
      case other => Seq(S.Unsupported(other.toString, "Unsupported class member", unparsable = false))
    }
    declarations ++ block(main.body, scope)
  }

  private def mentions(node: it.evadid.vm.parsing.generic.abstractions.GenericAST, name: String): Boolean = node match {
    case target: JavaTarget if target.name == name && target.locationString.isEmpty => true
    case other => other.getChildren().exists(mentions(_, name))
  }

  private def classDef(definition: JavaClassDef): ParsedStatement = {
    val source = s"class ${definition.name}"
    if definition.extendsType.nonEmpty || definition.implementsTypes.nonEmpty then S.Unsupported(source, "Inheritance", unparsable = false)
    else {
      val members = definition.body.statements.filterNot(member => member.isInstanceOf[JavaCommentStatement] || member.isInstanceOf[JavaClassDef] || member == JavaEmptyStatement)
      val fields = members.collect { case field: JavaVariableDeclaration => field }
      val methods = members.collect { case method: JavaMethodDef => method }
      if fields.exists(_.modifiers.contains("static")) then
        throw ProgrammingConversionException.unsupported("Static object fields are not supported by the shared expression model.")
      if members.size != fields.size + methods.size then S.Unsupported(source, "Class-body statements", unparsable = false)
      else {
        val owner = Owner(definition.name, fields.map(_.name).toSet,
          methods.filterNot(_.name == definition.name).map(method => method.name -> method.modifiers.contains("static")).toMap, isStatic = false)
        val parsedMethods = methods.map { method =>
          val kind = if method.returnType.isEmpty && method.name == definition.name then ParsedFunctionKind.Constructor
            else if method.modifiers.contains("static") then ParsedFunctionKind.StaticMethod else ParsedFunctionKind.Method
          function(method, kind, Scope(Some(owner.copy(isStatic = kind == ParsedFunctionKind.StaticMethod)), Set.empty, None))
        }
        val parsedFields = fields.map(field => S.Field(field.name, Some(parsedType(field.javaType)),
          field.value.map(expression(_, Scope(Some(owner), Set.empty, None)))))
        S.ClassDef(definition.name, parsedFields, parsedMethods, source)
      }
    }
  }

  private def function(method: JavaMethodDef, kind: ParsedFunctionKind, scope: Scope): S.FunctionDef = {
    val parameters = method.parameters.map(parameter => ParsedParameter(parameter.name, Some(parsedType(parameter.javaType))))
    val inner = parameters.foldLeft(scope)((current, parameter) => current.withLocal(parameter.name))
    S.FunctionDef(method.name, parameters, method.returnType.map(parsedType), block(method.body, inner), kind)
  }

  private def block(value: JavaExecutionBlock, scope: Scope): Seq[ParsedStatement] = statements(value.statements, scope)

  /** Declarations extend the scope of the statements that follow them. */
  private def statements(values: Seq[JavaStatement], scope: Scope): Seq[ParsedStatement] =
    values.foldLeft((Vector.empty[ParsedStatement], scope)) { case ((done, current), value) =>
      val next = value match {
        case declaration: JavaVariableDeclaration => current.withLocal(declaration.name)
        case _ => current
      }
      (done ++ statement(value, current), next)
    }._1

  private def statement(value: JavaStatement, scope: Scope): Seq[ParsedStatement] = value match {
    case JavaCommentStatement(text) => Seq(S.Comment(text))
    case JavaEmptyStatement | _: JavaImportStatement | _: JavaPackageStatement => Nil
    case definition: JavaClassDef => Seq(classDef(definition))
    case method: JavaMethodDef => Seq(function(method, ParsedFunctionKind.Function, scope))
    case JavaVariableDeclaration(name, javaType, Some(initial), _) => Seq(S.Assign(name, Some(parsedType(javaType)), expression(initial, scope)))
    case JavaVariableDeclaration(name, javaType, None, _) => Seq(S.Declare(name, parsedType(javaType)))
    case JavaAssignment(target, value) => Seq(assign(target, expression(value, scope), scope))
    case JavaAugAssignment(target, operator, value) =>
      Seq(assign(target, E.Binary(operator.stripSuffix("="), expression(target, scope), expression(value, scope)), scope))
    case JavaUpdateExpression(target: JavaTarget, operator, _) =>
      Seq(assign(target, E.Binary(operator.take(1), expression(target, scope), E.Literal("1")), scope))
    case JavaIfStatement(condition, thenBlock, elseBlock) =>
      Seq(S.If(expression(condition, scope), block(thenBlock, scope), elseBlock.toSeq.flatMap(block(_, scope))))
    case JavaWhileStatement(condition, body) => Seq(S.While(expression(condition, scope), block(body, scope)))
    case loop: JavaForStatement => Seq(forLoop(loop, scope))
    case JavaReturnStatement(value) => Seq(S.Return(value.map(expression(_, scope))))
    case JavaUnparsableStatement(source) => Seq(S.Unsupported(source, "This Java source contains unsupported or unfinished syntax.", unparsable = true))
    case value: JavaExpression => Seq(S.Evaluate(expression(value, scope)))
    case other => Seq(S.Unsupported(other.getClass.getSimpleName.stripPrefix("Java"), "Unsupported Java statement", unparsable = false))
  }

  private def assign(target: JavaTarget, value: ParsedExpression, scope: Scope): ParsedStatement =
    if target.sliceExpr.nonEmpty then S.Unsupported(s"${target.name}[...] = ...", "Array assignment is not supported", unparsable = false)
    else qualified(target, scope) match {
      case E.Name(name) => S.Assign(name, None, value)
      case E.Member(receiver, name) => S.AssignMember(receiver, name, value)
      case other => S.Unsupported(s"${target.name} = ...", "Unsupported assignment target", unparsable = false)
    }

  /** `for (int i = a; i < b; i++)`: a repeat when the counter is unused and counts from 0 to a literal. */
  private def forLoop(loop: JavaForStatement, scope: Scope): ParsedStatement = {
    def isIncrement(counter: String): Boolean = loop.update match {
      case Seq(JavaUpdateExpression(JavaTarget(`counter`, Seq(), None), "++", _)) => true
      case Seq(JavaAssignmentExpression(JavaTarget(`counter`, Seq(), None), "+=", JavaLiteral("1", _))) => true
      case _ => false
    }
    (loop.init, loop.condition) match {
      case (Seq(JavaVariableDeclaration(counter, _, Some(start), _)), Some(JavaOperationBinary(JavaTarget(name, Seq(), None), operator @ ("<" | "<="), limit)))
          if name == counter && isIncrement(counter) =>
        val inner = scope.withLocal(counter)
        (start, operator, limit) match {
          case (JavaLiteral("0", _: JAVA_INTEGER), "<", JavaLiteral(times, _: JAVA_INTEGER)) if !mentions(loop.bodyBlock, counter) && times.toIntOption.nonEmpty =>
            S.Repeat(times.toInt, block(loop.bodyBlock, scope))
          case _ =>
            val end = if operator == "<=" then expression(limit, scope) else limit match {
              case JavaLiteral(value, _: JAVA_INTEGER) if value.toIntOption.nonEmpty => E.Literal((value.toInt - 1).toString)
              case JavaOperationBinary(inner, "+", JavaLiteral("1", _)) => expression(inner, scope)
              case other => E.Binary("-", expression(other, scope), E.Literal("1"))
            }
            S.For(counter, expression(start, scope), end, block(loop.bodyBlock, inner))
        }
      case _ => S.Unsupported("for (...)", "Only counting for loops are supported", unparsable = false)
    }
  }

  // Expressions

  private val operators = Map("&&" -> "and", "||" -> "or", "!" -> "not")

  private def expression(value: JavaExpression, scope: Scope): ParsedExpression = value match {
    case JavaLiteral("null", _) => E.NoneValue
    case JavaLiteral(raw, _: JAVA_BOOL) => E.Literal(raw.capitalize)
    case JavaLiteral(raw, _) => E.Literal(raw)
    case target: JavaTarget if target.sliceExpr.nonEmpty => E.Unsupported(s"${target.name}[...]")
    case target: JavaTarget => qualified(target, scope)
    case JavaParenthesizedExpression(inner) => expression(inner, scope)
    case JavaOperationUnary(operator, operand) => E.Unary(operators.getOrElse(operator, operator), expression(operand, scope))
    case JavaOperationBinary(left, operator, right) =>
      E.Binary(operators.getOrElse(operator, operator), expression(left, scope), expression(right, scope))
    case JavaFunctionCall(target, arguments) => call(target.locationString :+ target.name, arguments.map(expression(_, scope)), scope)
    case JavaCallExpression(JavaAttributeAccess(receiver, name), arguments) =>
      names(receiver) match {
        case Some(path) => call(path :+ name, arguments.map(expression(_, scope)), scope)
        case None => E.MethodCall(expression(receiver, scope), name, arguments.map(expression(_, scope)))
      }
    case JavaAttributeAccess(receiver, name) => E.Member(expression(receiver, scope), name)
    case JavaNewExpression(javaType, arguments) => E.Construct(javaType.typenameInCode, arguments.map(expression(_, scope)))
    case other => E.Unsupported(other.getClass.getSimpleName.stripPrefix("Java"))
  }

  private def names(value: JavaExpression): Option[Seq[String]] = value match {
    case JavaTarget(name, location, None) => Some(location :+ name)
    case JavaAttributeAccess(receiver, name) => names(receiver).map(_ :+ name)
    case _ => None
  }

  /** Resolves implicit `this`, the module qualifier and `this.` prefixes. */
  private def qualified(target: JavaTarget, scope: Scope): ParsedExpression = {
    val path = (target.locationString :+ target.name).dropWhile(name => scope.module.contains(name) && target.locationString.nonEmpty)
    path match {
      case Seq("this") => E.Self
      case Seq(name) if !scope.locals(name) && scope.owner.exists(owner => owner.fields(name) && !owner.isStatic) => E.Member(E.Self, name)
      case Seq(name) => E.Name(name)
      case head +: rest => rest.foldLeft[ParsedExpression](if head == "this" then E.Self else E.Name(head))(E.Member(_, _))
    }
  }

  private def call(rawPath: Seq[String], arguments: Seq[ParsedExpression], scope: Scope): ParsedExpression = {
    val path = (if rawPath.size > 1 && scope.module.contains(rawPath.head) then rawPath.tail else rawPath)
      .mkString(".").stripPrefix("java.lang.").split('.').toSeq
    (path, arguments) match {
      // The Java printer spells Python's floor division and power with java.lang.Math.
      case (Seq("Math", "floor"), Seq(E.Binary("/", left, right))) => E.Binary("//", left, right)
      case (Seq("Math", "pow"), Seq(base, exponent)) => E.Binary("**", base, exponent)
      case (Seq(name), _) if !scope.locals(name) && scope.owner.exists(_.methods.contains(name)) =>
        val owner = scope.owner.get
        if owner.methods(name) then E.Call(Seq(owner.name, name), arguments)
        else if owner.isStatic then throw IllegalArgumentException(s"Instance method '$name' needs an object.")
        else E.MethodCall(E.Self, name, arguments)
      case _ => E.Call(path, arguments)
    }
  }

  private def parsedType(value: JavaType[?]): ParsedType = value match {
    case _: (JAVA_INTEGER | JAVA_INTEGER_HEX | JAVA_INTEGER_OCT | JAVA_INTEGER_BIN | JAVA_FLOAT) => ParsedType.Numeric
    case _: JAVA_BOOL => ParsedType.Boolean
    case _: JAVA_STRING => ParsedType.Text
    case _: JAVA_ANY => ParsedType.AnyValue
    case named: JAVA_UNPARSABLE_TYPE => named.str match {
      case "void" => ParsedType.Unit
      case "Object" => ParsedType.AnyValue
      case "Date" | "LocalDate" => ParsedType.Date
      case other => ParsedType.Named(other)
    }
    case _ => throw ProgrammingConversionException.unsupported(s"Unsupported Java type: ${value.typenameInCode}")
  }
}
