package it.evadid.vm.conversion.parsed

import it.evadid.core.datastructures.language.AppLanguage.{English, ProgrammingLanguage}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.defining.BeDefineFunction.{BeFunctionTypeInfo, Constructor, Method, StaticMethod}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeReturn
import it.evadid.vm.code.usage.{BeAssignMember, BeAssignVariable, BeCallTarget, BeCurrentInstance, BeFunctionCall, BeMemberAccess, BeUseValue}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.parsed.SymbolTable.{CurrentlyKnownStructures, ParseContext, ParsedSources}
import it.evadid.vm.parsing.source.{SourceIdentity, SourceSpan}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}
import it.evadid.vm.types.BeDataType.BeUnionAllowedTypes

/** Second front-end step: binds a [[ParsedProgram]] to definitions and builds the shared tree.
  *
  * Classes are declared (signatures first) before any body is read, and each block reserves the
  * names of the functions and classes it declares, so calls bind to user definitions before falling
  * back to known names such as turtle commands or `print`; library calls bind only to the libraries `environment`
  * provides. Each step reads the immutable [[ParseContext]] and passes on what it learned ([[ParseStep]]), in
  * source order, including where definitions and placeholders came from ([[ParsedSources]]).
  */
final class ParsedProgramBuilder(language: ProgrammingLanguage, environment: ProgrammingEnvironment = ProgrammingEnvironment.all) {
  import ParsedProgramBuilder.Result

  def build(source: ParsedProgram): Result = {
    val program = ParsedTypeInference.infer(source)
    val classes = program.statements.collect { case definition: S.ClassDef => definition }
    val named = classes.foldLeft(ParseContext.initial(CurrentlyKnownStructures.fromKnown(environment.knownStructures), environment)) { (context, definition) =>
      context.registerClass(definition.name, BeDefineClass(BeEntityName.fromCodeString(definition.name), Nil, Nil))
    }
    val declared = classes.foldLeft(named)((context, definition) => context.registerClass(definition.name, classSignatures(definition, context)._1))
    val (statements, context) = block(program.statements).run(declared)
    Result(BeSequence.optionalBody(statements), context, context.sources.functions, context.sources.origins)
  }

  /** `value`, recorded as coming from `origin` in the source. */
  private def located(value: BeExpression, origin: Option[SourceSpan]): ParseStep[BeExpression] =
    ParseStep(context => (value, origin.fold(context)(span => context.withSources(_.origin(value, span)))))

  private def keep(expression: BeExpression): Boolean = expression match {
    case BeSequence(body, info) => body.nonEmpty || info.mustEvaluateTo.nonEmpty
    case _ => true
  }

  private def block(statements: Seq[ParsedStatement]): ParseStep[List[BeExpression]] = for {
    _ <- ParseStep.update(_.reserveCallableNames(statements.collect {
      case definition: S.FunctionDef => definition.name
      case definition: S.ClassDef => definition.name
    }.toSet))
    built <- ParseStep.traverse(statements)(statement)
  } yield built.filter(keep)

  private def body(statements: Seq[ParsedStatement]): ParseStep[BeSequence] = block(statements).map(BeSequence.optionalBody)

  private def defineVariable(name: String, dataType: BeDataType): ParseStep[BeDefineVariable] = ParseStep(_.defineVariable(name, dataType))

  private def defineParameter(parameter: ParsedParameter): ParseStep[BeDefineVariable] = defineVariable(parameter.name, dataType(parameter.typeHint))

  private def statement(parsed: ParsedStatement): ParseStep[BeExpression] = parsed match {
    case definition: S.ClassDef => buildClass(definition)
    case definition: S.FunctionDef => buildFunction(definition)
    case S.Declare(name, typeHint) => defineVariable(name, dataType(typeHint))
    // Assignments bind to module variables already; `global` makes sure the module variable exists.
    case S.Global(names) =>
      ParseStep.traverse(names)(name => ParseStep(_.moduleVariable(name, BeDataType.AnyType))).map(_ => BeExpression.pass)
    case S.Assign(name, Some(typeHint), value) => for {
      variable <- defineVariable(name, dataType(typeHint))
      built <- expression(value)
    } yield BeAssignVariable(variable, built)
    case S.Assign(name, None, value) => for {
      built <- expression(value)
      variable <- ParseStep(_.assignVariable(name, inferredType(built)))
    } yield BeAssignVariable(variable, built)
    case S.AssignMember(receiver, member, value) =>
      expression(E.Member(receiver, member)).flatMap {
        case BeMemberAccess(instance, field) => expression(value).map(BeAssignMember(instance, field, _))
        case _ => expression(value).map(unknownAssignment(s"${describe(receiver)}.$member", _))
      }
    case S.If(condition, thenBody, elseBody) => for {
      test <- expression(condition)
      thenBranch <- body(thenBody)
      elseBranch <- body(elseBody)
    } yield BeIfElse(BeSequence.conditionalBody(List(test)), thenBranch, elseBranch)
    case S.While(condition, loopBody) => for {
      test <- expression(condition)
      repeated <- body(loopBody)
    } yield BeWhile(BeSequence.conditionalBody(List(test)), repeated)
    case S.Repeat(times, loopBody) => for {
      count <- expression(times)
      repeated <- body(loopBody)
    } yield BeRepeatNr(count, repeated)
    case S.For(variable, start, end, loopBody, up) => for {
      first <- expression(start)
      last <- expression(end)
      counter <- defineVariable(variable, BeDataType.Numeric)
      repeated <- body(loopBody)
    } yield BeFor(counter, first, last, repeated, if up then BeFor.Direction.Up else BeFor.Direction.Down)
    case S.Return(value) => ParseStep.traverse(value.toList)(expression).map(values => BeReturn(values.headOption))
    case S.Evaluate(value) => expression(value)
    case S.Comment(text) => ParseStep.pure(BeSingleLineComment(LanguageMap.universalMap(text)))
    case S.Pass => ParseStep.pure(BeExpression.pass)
    case value: S.Unsupported if value.unparsable => located(BeExpressionUnparsable(value.source, value.reason, Some(language)), value.origin)
    case value: S.Unsupported => located(BeExpressionUnsupported(value.source, Some(language), value.reason), value.origin)
  }

  /** Parameters and locals live in the function's own scope; its signature is known to its body for recursion. */
  private def buildFunction(definition: S.FunctionDef): ParseStep[BeDefineFunction] = for {
    _ <- ParseStep.update(_.pushScope)
    parameters <- ParseStep.traverse(definition.parameters)(defineParameter)
    signature = BeDefineFunction(parameters, result(definition.returnType), BeExpression.pass,
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(definition.name)))
    _ <- ParseStep.update(_.declareFunction(definition.name, signature))
    built <- body(definition.body).map(code => signature.copy(body = code))
    _ <- ParseStep.update(_.popScope.registerFunction(definition.name, built)
      .withSources(sources => definition.origin.fold(sources)(sources.function(_, built))))
  } yield built

  /** A function's result; `None`/`void` and a missing hint both mean that it returns nothing. */
  private def result(returnType: Option[ParsedType]): Option[BeDefineVariable] =
    returnType.filterNot(_ == ParsedType.Unit).map(typeHint => BeDefineVariable(BeEntityName.fromUniversalNameInParts("result"), dataType(typeHint)))

  // Classes: signatures are bound before bodies; definitions never contain a receiver parameter.
  // Field values and method bodies are read in child contexts, so their names stay local to them.

  private def kind(definition: S.FunctionDef): BeDefineFunction.BeFunctionType = definition.kind match {
    case ParsedFunctionKind.StaticMethod => StaticMethod
    case ParsedFunctionKind.Constructor => Constructor
    case _ => Method
  }

  private def methodSignature(owner: BeDefineClass, method: S.FunctionDef): BeDefineFunction =
    BeDefineFunction(
      method.parameters.map(parameter => BeDefineVariable(BeEntityName.fromCodeString(parameter.name), dataType(parameter.typeHint))).toList,
      result(method.returnType),
      BeExpression.pass,
      BeFunctionTypeInfo(Some(owner), Option.when(kind(method) != Constructor)(BeEntityName.fromCodeString(method.name)), kind(method)))

  private def withParameters(context: ParseContext, parameters: Seq[ParsedParameter]): ParseContext =
    ParseStep.traverse(parameters)(defineParameter).run(context)._2

  /** The fields of a class, and the sources recorded while reading their initial values. */
  private def fields(definition: S.ClassDef, context: ParseContext): (List[BeDefineVariable], ParsedSources) = {
    val (read, sources) = definition.fields.foldLeft((Vector.empty[BeDefineVariable], context.sources)) { case ((done, sources), field) =>
      val fieldContext = withParameters(context.child().withSources(_ => sources), field.assigned.toSeq.flatMap(_._2))
      val source = field.initial.orElse(field.assigned.map(_._1))
      lazy val value = source.map(expression(_).run(fieldContext))
      val constructed = source.collect {
        case E.Call(Seq(name), _) => name
        case E.Construct(name, _) => name
      }.flatMap(context.lookupClass).map(owner => BeDataType.ClassType(owner.name))
      val fieldType = field.typeHint.map(dataType).orElse(constructed).orElse(value.map(run => inferredType(run._1))).getOrElse(BeDataType.AnyType)
      val initial = Option.when(field.initial.nonEmpty)(value).flatten
      (done :+ BeDefineVariable(BeEntityName.fromCodeString(field.name), fieldType, initial.map(_._1)), initial.fold(sources)(_._2.sources))
    }
    (read.groupBy(_.name).values.map(_.head).toList.sortBy(_.name.universalInterpretation()), sources)
  }

  private def classSignatures(definition: S.ClassDef, context: ParseContext): (BeDefineClass, ParsedSources) = {
    val (attributes, sources) = fields(definition, context)
    val owner = BeDefineClass(BeEntityName.fromCodeString(definition.name), attributes, Nil)
    (owner.copy(methods = definition.methods.map(methodSignature(owner, _)).toList), sources)
  }

  /** Method bodies are read in child contexts; the sources they record are passed on to the next method and back. */
  private def buildClass(definition: S.ClassDef): ParseStep[BeDefineClass] = ParseStep { context =>
    val (signatures, fieldSources) = classSignatures(definition, context)
    val owner = signatures.copy(methods = Nil)
    val classContext = context.child().registerClass(definition.name, signatures)
    val (methods, sources) = definition.methods.foldLeft((Vector.empty[BeDefineFunction], fieldSources)) { case ((done, sources), method) =>
      val methodContext = withParameters(classContext.child(Option.when(kind(method) != StaticMethod)(owner)).withSources(_ => sources), method.parameters)
      val (code, after) = body(method.body).run(methodContext)
      val built = methodSignature(owner, method).copy(body = code)
      (done :+ built, method.origin.fold(after.sources)(after.sources.function(_, built)))
    }
    val built = owner.copy(methods = methods.toList)
    (built, context.registerClass(definition.name, built).withSources(_ => sources))
  }

  // Expressions

  private def arguments(parsed: Seq[ParsedExpression]): ParseStep[List[BeExpression]] = ParseStep.traverse(parsed)(expression)

  private def expression(parsed: ParsedExpression): ParseStep[BeExpression] = parsed match {
    case E.Literal(raw) => ParseStep.pure(BeUseValue(BeDataValueLiteral(raw), None))
    case E.NoneValue => ParseStep.pure(BeUseValue(BeDataValueUnit(), None))
    case E.Self => ParseStep.inspect(_.currentClass.map(owner => BeCurrentInstance(owner.name))
      .getOrElse(BeExpressionUnsupported(if language.name == "Java" then "this" else "self", Some(language))))
    case E.Name(name) =>
      ParseStep(_.assignVariable(name, BeDataType.AnyType)).map(variable => BeUseValue(BeUseValueReference(variable), Some(variable)))
    case E.Unary(operator, operand) => for {
      value <- expression(operand)
      function <- ParseStep(_.resolveOperator(operator, 1, List(value)))
    } yield BeFunctionCall.fromDefinition(function, Map(function.inputs.head -> value))
    case E.Binary(operator, left, right) => for {
      first <- expression(left)
      second <- expression(right)
      function <- ParseStep(_.resolveOperator(operator, 2, List(first, second)))
    } yield BeFunctionCall.fromDefinition(function, Map(function.inputs.head -> first, function.inputs(1) -> second))
    case E.Call(path, values) => arguments(values).flatMap(call(path, _))
    case E.MethodCall(receiver, name, values) => for {
      instance <- expression(receiver)
      bound <- arguments(values)
      call <- ParseStep.inspect(instanceCall(instance, name, name, bound, _))
    } yield call
    case E.Member(receiver, name) => for {
      instance <- expression(receiver)
      access <- ParseStep.inspect(_.classForType(instance.staticInformationExpression.staticType).flatMap { owner =>
        owner.attributes.find(_.name.universalInterpretation() == BeEntityName.fromCodeString(name).universalInterpretation())
          .map(member => BeMemberAccess(instance, member))
      }.getOrElse(BeExpressionUnsupported(s"${describe(receiver)}.$name", Some(language))))
    } yield access
    case E.Construct(className, values) => for {
      owner <- ParseStep.inspect(_.lookupClass(className)
        .getOrElse(throw IllegalArgumentException(s"Cannot convert construction of unknown class '$className'.")))
      bound <- arguments(values)
    } yield bind(className, BeCallTarget.Constructor(constructor(owner)), bound)
    case value: E.Unsupported => located(BeExpressionUnsupported(value.source, Some(language)), value.origin)
  }

  /** An assignment to an attribute nothing defines (`Config.limit = 3`) keeps its source, like a read of one;
    * [[it.evadid.vm.conversion.dependencies.UnknownDependencies]] collects its target.
    */
  private def unknownAssignment(target: String, value: BeExpression): BeExpressionUnsupported = {
    val printed = value.structureInfo.toStringInLanguage(language, English).trim.stripSuffix(";")
    BeExpressionUnsupported(s"$target = $printed${if language.name == "Java" then ";" else ""}", Some(language),
      s"Cannot resolve the member assignment to '$target'.")
  }

  private def describe(parsed: ParsedExpression): String = parsed match {
    case E.Name(name) => name
    case E.Self => if language.name == "Java" then "this" else "self"
    case E.Member(receiver, name) => s"${describe(receiver)}.$name"
    case other => other.toString
  }

  private def bind(name: String, target: BeCallTarget, arguments: List[BeExpression]): BeFunctionCall = {
    require(arguments.size == target.definition.inputs.size,
      s"Call '$name' expects ${target.definition.inputs.size} arguments, received ${arguments.size}.")
    BeFunctionCall(target, target.definition.inputs.zip(arguments).toMap)
  }

  private def constructor(owner: BeDefineClass): BeDefineFunction =
    owner.methods.find(_.functionTypeInfo.funcType == Constructor).getOrElse(
      BeDefineFunction(Nil, None, BeExpression.pass, BeFunctionTypeInfo(Some(owner.copy(methods = Nil)), None, Constructor)))

  private def findMethod(owner: BeDefineClass, sourceName: String): BeDefineFunction =
    owner.methods.find(_.functionTypeInfo.displayName.universalInterpretation() == BeEntityName.fromCodeString(sourceName).universalInterpretation())
      .getOrElse(throw IllegalArgumentException(s"Class '${owner.name.universalInterpretation()}' has no method '$sourceName'."))

  private def call(path: Seq[String], arguments: List[BeExpression]): ParseStep[BeExpression] = ParseStep.context.flatMap { context =>
    val name = path.mkString(".")
    def isVariable(source: String) = context.lookupVariable(source).nonEmpty
    context.lookupClass(name).filter(_ => !isVariable(name)) match {
      case Some(owner) => ParseStep.pure(bind(name, BeCallTarget.Constructor(constructor(owner)), arguments))
      case None if path.size > 1 =>
        val receiverSource = path.init.mkString(".")
        val methodName = path.last
        context.lookupClass(receiverSource).filter(_ => !isVariable(receiverSource)) match {
          case Some(owner) =>
            val method = findMethod(owner, methodName)
            require(method.functionTypeInfo.funcType == StaticMethod, s"Instance method '$name' needs an object.")
            ParseStep.pure(bind(name, BeCallTarget.StaticMethod(method), arguments))
          case None if path.head == "self" || path.head == "this" ||
              context.lookupVariable(path.head).exists(variable => context.classForType(variable.variableType).nonEmpty) =>
            val receiverPath = path.init
            val receiver = receiverPath.tail.foldLeft[ParsedExpression](
              if receiverPath.head == "self" || receiverPath.head == "this" then E.Self else E.Name(receiverPath.head))(E.Member(_, _))
            expression(receiver).map(instanceCall(_, name, methodName, arguments, context))
          case None => moduleFunction(name, arguments)
        }
      case None => moduleFunction(name, arguments)
    }
  }

  private def instanceCall(instance: BeExpression, name: String, methodName: String, arguments: List[BeExpression], context: ParseContext): BeFunctionCall = {
    val owner = context.classForType(instance.staticInformationExpression.staticType)
      .getOrElse(throw IllegalArgumentException(s"Cannot resolve the receiver of '$name'."))
    val method = findMethod(owner, methodName)
    require(method.functionTypeInfo.funcType == Method, s"Static method '$name' must be called on its class.")
    bind(name, BeCallTarget.InstanceMethod(instance, method), arguments)
  }

  private def moduleFunction(name: String, arguments: List[BeExpression]): ParseStep[BeFunctionCall] = for {
    resolved <- ParseStep(_.resolveFunction(name, arguments.length))
    function <- ParseStep(_.ensureFunctionArity(name, resolved, arguments.length))
  } yield BeFunctionCall.fromDefinition(function, function.inputs.zip(arguments).toMap)

  private def inferredType(value: BeExpression): BeDataType = value.staticInformationExpression.staticType match {
    case BeDataType.Error => BeDataType.AnyType
    case other => other
  }

  def dataType(parsed: ParsedType): BeDataType = parsed match {
    case ParsedType.AnyValue => BeDataType.AnyType
    case ParsedType.Numeric => BeDataType.Numeric
    case ParsedType.Boolean => BeDataType.Boolean
    case ParsedType.Text => BeDataType.String
    case ParsedType.Date => BeDataType.Date
    case ParsedType.Unit => BeDataType.Unit
    case ParsedType.Named(name) => BeDataType.ClassType(BeEntityName.fromCodeString(name))
    case ParsedType.Union(options) => BeUnionAllowedTypes(options.map(dataType).toSet)
  }

  private def dataType(parsed: Option[ParsedType]): BeDataType = parsed.map(dataType).getOrElse(BeDataType.AnyType)
}


/** A building step: reads the [[ParseContext]] and returns its result with the context extended by what it learned.
  * Steps compose in source order with `for` comprehensions.
  */
private final case class ParseStep[+A](run: ParseContext => (A, ParseContext)) {
  def map[B](f: A => B): ParseStep[B] = ParseStep { context =>
    val (value, next) = run(context)
    (f(value), next)
  }

  def flatMap[B](f: A => ParseStep[B]): ParseStep[B] = ParseStep { context =>
    val (value, next) = run(context)
    f(value).run(next)
  }
}

private object ParseStep {
  def pure[A](value: A): ParseStep[A] = ParseStep(context => (value, context))

  val context: ParseStep[ParseContext] = ParseStep(context => (context, context))

  /** Reads the context without changing it. */
  def inspect[A](f: ParseContext => A): ParseStep[A] = ParseStep(context => (f(context), context))

  def update(f: ParseContext => ParseContext): ParseStep[Unit] = ParseStep(context => ((), f(context)))

  /** Runs `f` for each item in order, each step seeing what the previous ones learned. */
  def traverse[A, B](items: Seq[A])(f: A => ParseStep[B]): ParseStep[List[B]] = ParseStep { context =>
    val (reversed, last) = items.foldLeft((List.empty[B], context)) { case ((built, current), item) =>
      val (value, next) = f(item).run(current)
      (value :: built, next)
    }
    (reversed.reverse, last)
  }
}

object ParsedProgramBuilder {
  final case class Result(expression: BeSequence, context: ParseContext,
      sourceFunctions: Map[SourceSpan, BeDefineFunction], sourceOrigins: Map[SourceIdentity[BeExpression], SourceSpan])
}
