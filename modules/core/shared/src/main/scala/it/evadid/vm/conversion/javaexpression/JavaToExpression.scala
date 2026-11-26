package it.evadid.vm.conversion.javaexpression

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionKind}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.compilation.java.turtle.{JavaTurtleCompiler, JavaTurtleInputLimits, JavaTurtleProgram, JavaTurtleSourceParser}
import it.evadid.vm.conversion.dependencies.{KnownDependencies, ProgrammingEnvironment}
import it.evadid.vm.conversion.expressionjava.JavaSpelling
import it.evadid.vm.conversion.parsed.{ParsedExpression, ParsedFunctionKind, ParsedParameter, ParsedProgram, ParsedProgramBuilder, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.JavaSourceMap
import it.evadid.vm.parsing.source.JavaSyntax
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
object JavaToExpression {
  /** Step 3 for Java ([[it.evadid.vm.parsing.source.SourceParsing]]): the expression tree of the step-2 syntax tree,
    * binding library calls to the libraries `environment` provides. Constructs without a shared form stay
    * placeholders, as in Python; conversion to another language reports them with their position
    * ([[it.evadid.vm.conversion.ProgrammingStateDerivations]]). A statement the grammar kept unreadable is refused.
    */
  def convert(syntax: JavaSyntax, neutral: ParsedProgram, environment: ProgrammingEnvironment): (ProgrammingStateBeExpression, ParsedProgramBuilder.Result) = {
    syntax.readable.left.foreach(error => throw error)
    val built = ParsedProgramBuilder(Java, environment).build(neutral)
    ProgrammingStateBeExpression(BeStartProgram(built.expression)) -> built
  }

  /** The language-neutral program of the step-2 syntax tree. */
  def neutral(syntax: JavaSyntax): ParsedProgram = new JavaAstToParsed(syntax).apply(syntax.program)

}

/** Translates Java syntax into the language-neutral [[ParsedProgram]]. */
private final class JavaAstToParsed(syntax: JavaSyntax) {
  private val lines = it.evadid.vm.parsing.source.SourceLines(syntax.structured.source)
  private def origin(node: JavaAST) = syntax.sourceMap.rangeOf(node).map(range => lines.span(range.start, range.end))
  private def located[A](node: JavaAST)(result: => A): A = try result catch {
    case error: ProgrammingConversionException if error.error.span.isEmpty =>
      val span = origin(node)
      throw new ProgrammingConversionException(error.getMessage, error.error.copy(span = span,
        line = span.map(_.startLine), column = span.map(value => lines.column(value.start))))
  }

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

  private def module(definition: JavaClassDef, scope: Scope): Seq[ParsedStatement] = located(definition) {
    val members = definition.body.statements.filterNot(member => member.isInstanceOf[JavaCommentStatement] || member.isInstanceOf[JavaClassDef] || member == JavaEmptyStatement)
    val isStaticMember: JavaStatement => Boolean = {
      case field: JavaVariableDeclaration => field.modifiers.contains("static")
      case method: JavaMethodDef => method.modifiers.contains("static")
      case _ => true
    }
    if members.exists(isStaticMember) && !members.forall(isStaticMember) then
      throw ProgrammingConversionException.unsupported("Mixed static and instance members are not supported by the shared expression tree.")
    val main = members.collect { case method: JavaMethodDef if isEntryPoint(method) => method }.sortBy(_.parameters.isEmpty).head
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

  private def classDef(definition: JavaClassDef): ParsedStatement = located(definition) {
    classDefinition(definition) match {
      case value: S.Unsupported => value.copy(origin = origin(definition))
      case other => other
    }
  }

  private def classDefinition(definition: JavaClassDef): ParsedStatement = {
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
    S.FunctionDef(method.name, parameters, method.returnType.map(parsedType), block(method.body, inner), kind, origin(method))
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

  private def statement(value: JavaStatement, scope: Scope): Seq[ParsedStatement] = located(value) {
    statementValue(value, scope).map {
      case unsupported: S.Unsupported => unsupported.copy(origin = unsupported.origin.orElse(origin(value)))
      case other => other
    }
  }

  private def statementValue(value: JavaStatement, scope: Scope): Seq[ParsedStatement] = value match {
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
    case JavaAssignmentExpression(target, "=", value) => Seq(assign(target, expression(value, scope), scope))
    case JavaAssignmentExpression(target, operator, value) =>
      Seq(assign(target, E.Binary(operator.stripSuffix("="), expression(target, scope), expression(value, scope)), scope))
    case JavaIfStatement(condition, thenBlock, elseBlock) =>
      Seq(S.If(expression(condition, scope), block(thenBlock, scope), elseBlock.toSeq.flatMap(block(_, scope))))
    case JavaWhileStatement(condition, body) => Seq(S.While(expression(condition, scope), block(body, scope)))
    case loop: JavaForStatement => forLoop(loop, scope)
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

  /** A counting `for` declares one counter, compares it with a bound (`<`, `<=`, `>`, `>=`) and
    * changes it by a whole-number literal in the direction of the bound, without the body assigning
    * the counter or a name in the bound. It becomes a fixed repetition, a counting loop or a stepped
    * while loop ([[ParsedStatement.countingLoop]]); the counter is observed only in the body, as Java
    * scopes it to the loop. Calls are assumed not to change the bound. Any other loop whose updates
    * are assignments runs its initialisation and then a while loop that ends with the updates.
    */
  private def forLoop(loop: JavaForStatement, scope: Scope): Seq[ParsedStatement] = {
    def isUpdate(value: JavaExpression): Boolean = value match {
      case JavaUpdateExpression(_: JavaTarget, _, _) | _: JavaAssignmentExpression => true
      case _ => false
    }
    countingLoop(loop, scope).getOrElse {
      if loop.update.forall(isUpdate) then {
        val inner = loop.init.foldLeft(scope) {
          case (current, declaration: JavaVariableDeclaration) => current.withLocal(declaration.name)
          case (current, _) => current
        }
        S.loopWithUpdate(statements(loop.init, scope), loop.condition.map(expression(_, inner)).getOrElse(E.Literal("True")),
          block(loop.bodyBlock, inner), loop.update.flatMap(statement(_, inner)))
      } else Seq(S.Unsupported("for (...)", "For-loop updates must be assignments or increments", unparsable = false))
    }
  }

  private def countingLoop(loop: JavaForStatement, scope: Scope): Option[Seq[ParsedStatement]] = loop match {
    case JavaForStatement(Seq(JavaVariableDeclaration(counter, _, Some(start), _)),
        Some(JavaOperationBinary(JavaTarget(compared, Seq(), None), comparison @ ("<" | "<=" | ">" | ">="), limit)), Seq(update), body)
        if compared == counter =>
      def literal(value: JavaExpression): Option[Int] = E.wholeNumber(expression(value, scope))
      val step = update match {
        case JavaUpdateExpression(JavaTarget(`counter`, Seq(), None), operator, _) => Some(if operator == "++" then 1 else -1)
        case JavaAssignmentExpression(JavaTarget(`counter`, Seq(), None), "+=", amount) => literal(amount)
        case JavaAssignmentExpression(JavaTarget(`counter`, Seq(), None), "-=", amount) => literal(amount).map(-_)
        case JavaAssignmentExpression(JavaTarget(`counter`, Seq(), None), "=", JavaOperationBinary(JavaTarget(`counter`, Seq(), None), operator @ ("+" | "-"), amount)) =>
          literal(amount).map(value => if operator == "+" then value else -value)
        case _ => None
      }
      val ascending = comparison.startsWith("<")
      val stable = !assigned(body).exists(name => name == counter || mentions(limit, name))
      step.filter(value => value != 0 && (value > 0) == ascending && stable).map { value =>
        val bound = expression(limit, scope)
        val stop = comparison match {
          case "<=" => E.offset(bound, 1)
          case ">=" => E.offset(bound, -1)
          case _ => bound
        }
        S.countingLoop(counter, expression(start, scope), stop, value, block(body, scope.withLocal(counter)), counterObserved = mentions(body, counter))
      }
    case _ => None
  }

  /** Names a statement assigns, including increments. */
  private def assigned(node: it.evadid.vm.parsing.generic.abstractions.GenericAST): Set[String] = (node match {
    case JavaAssignment(target, _) => Set(target.name)
    case JavaAugAssignment(target, _, _) => Set(target.name)
    case JavaAssignmentExpression(target, _, _) => Set(target.name)
    case JavaUpdateExpression(target: JavaTarget, _, _) => Set(target.name)
    case _ => Set.empty[String]
  }) ++ node.getChildren().flatMap(assigned)

  // Expressions


  private def expression(value: JavaExpression, scope: Scope): ParsedExpression = located(value) {
    expressionValue(value, scope) match {
      case unsupported: E.Unsupported => unsupported.copy(origin = unsupported.origin.orElse(origin(value)))
      case other => other
    }
  }

  private def expressionValue(value: JavaExpression, scope: Scope): ParsedExpression = value match {
    case JavaLiteral("null", _) => E.NoneValue
    case JavaLiteral(raw, _: JAVA_BOOL) => E.Literal(raw.capitalize)
    case JavaLiteral(raw, _) => E.Literal(raw)
    case target: JavaTarget if target.sliceExpr.nonEmpty => E.Unsupported(s"${target.name}[...]")
    case target: JavaTarget => qualified(target, scope)
    case JavaParenthesizedExpression(inner) => expression(inner, scope)
    case JavaCastExpression(javaType, operand) =>
      JavaSpelling.cast(javaType.typenameInCode).fold[ParsedExpression](E.Unsupported(s"(${javaType.typenameInCode}) ..."))(
        builtin => E.Call(Seq(builtin), Seq(expression(operand, scope))))
    case JavaOperationUnary("!", JavaCallExpression(JavaAttributeAccess(receiver, "equals"), Seq(other))) =>
      E.Binary("!=", expression(receiver, scope), expression(other, scope))
    case JavaOperationUnary("!", JavaCallExpression(JavaAttributeAccess(receiver, "isEmpty"), Seq())) =>
      E.Binary("!=", expression(receiver, scope), E.Literal("\"\""))
    case JavaOperationUnary(operator, operand) => E.Unary(JavaSpelling.operators.getOrElse(operator, operator), expression(operand, scope))
    case JavaOperationBinary(left, operator, right) =>
      E.Binary(JavaSpelling.operators.getOrElse(operator, operator), expression(left, scope), expression(right, scope))
    case JavaFunctionCall(target, arguments) => call(target.locationString :+ target.name, arguments.map(expression(_, scope)), scope)
    // The Java printer spells text equality and emptiness with String methods (JavaSpelling).
    case JavaCallExpression(JavaAttributeAccess(receiver, "equals"), Seq(other)) => E.Binary("==", expression(receiver, scope), expression(other, scope))
    case JavaCallExpression(JavaAttributeAccess(receiver, "isEmpty"), Seq()) => E.Binary("==", expression(receiver, scope), E.Literal("\"\""))
    case JavaCallExpression(JavaAttributeAccess(receiver, name), arguments) =>
      names(receiver) match {
        case Some(path) => call(path :+ name, arguments.map(expression(_, scope)), scope)
        case None => E.MethodCall(expression(receiver, scope), name, arguments.map(expression(_, scope)))
      }
    // `Main.count` reads a module variable of the entry class.
    case JavaAttributeAccess(JavaTarget(owner, Seq(), None), name) if scope.module.contains(owner) && !scope.locals(owner) =>
      qualified(JavaTarget(name), scope)
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
      // Library helpers are printed as static methods of their dependency class.
      case (qualified, _) if KnownDependencies.functionForJavaCall(qualified).nonEmpty =>
        E.Call(Seq(KnownDependencies.functionForJavaCall(qualified).get), arguments)
      case (qualified, _) if JavaSpelling.calls.contains(qualified) => E.Call(Seq(JavaSpelling.calls(qualified)), arguments)
      // Other java.lang.Math methods have no shared built-in; a plain call would print as an unimported `math.…`.
      case (Seq("Math", name), _) => E.Unsupported(s"Math.$name(...)")
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
