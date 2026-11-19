package it.evadid.vm.conversion.parsed

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
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
import it.evadid.vm.conversion.parsed.SymbolTable.{CurrentlyKnownStructures, KnownStructure, ParseContext}
import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}
import it.evadid.vm.types.BeDataType.BeUnionAllowedTypes

/** Second front-end step: binds a [[ParsedProgram]] to definitions and builds the shared tree.
  *
  * Classes are declared (signatures first) before any body is read, and each block reserves the
  * names of the functions and classes it declares, so calls bind to user definitions before falling
  * back to known names such as turtle commands or `print`.
  */
final class ParsedProgramBuilder(language: ProgrammingLanguage,
    knownStructures: Seq[KnownStructure] = SymbolTable.defaultKnownStructures) {

  final case class Result(expression: BeSequence, context: ParseContext)

  def build(source: ParsedProgram): Result = {
    val program = ParsedTypeInference.infer(source)
    val context = new ParseContext(CurrentlyKnownStructures.fromKnown(knownStructures))
    program.statements.collect { case definition: S.ClassDef => definition }.foreach { definition =>
      context.registerClass(definition.name, BeDefineClass(BeEntityName.fromCodeString(definition.name), Nil, Nil))
    }
    program.statements.collect { case definition: S.ClassDef => definition }.foreach { definition =>
      context.registerClass(definition.name, classSignatures(definition, context))
    }
    Result(BeSequence.optionalBody(block(program.statements, context)), context)
  }

  private def keep(expression: BeExpression): Boolean = expression match {
    case BeSequence(body, info) => body.nonEmpty || info.mustEvaluateTo.nonEmpty
    case _ => true
  }

  private def block(statements: Seq[ParsedStatement], context: ParseContext): List[BeExpression] = {
    context.reserveCallableNames(statements.collect {
      case definition: S.FunctionDef => definition.name
      case definition: S.ClassDef => definition.name
    }.toSet)
    statements.toList.map(statement(_, context)).filter(keep)
  }

  private def body(statements: Seq[ParsedStatement], context: ParseContext): BeSequence =
    BeSequence.optionalBody(block(statements, context))

  private def statement(parsed: ParsedStatement, context: ParseContext): BeExpression = parsed match {
    case definition: S.ClassDef => buildClass(definition, context)
    case definition: S.FunctionDef => buildFunction(definition, context)
    case S.Declare(name, typeHint) => context.defineVariable(name, dataType(typeHint))
    // Assignments bind to module variables already; `global` makes sure the module variable exists.
    case S.Global(names) =>
      names.foreach(context.moduleVariable(_, BeDataType.AnyType))
      BeExpression.pass
    case S.Assign(name, Some(typeHint), value) =>
      val variable = context.defineVariable(name, dataType(typeHint))
      BeAssignVariable(variable, expression(value, context))
    case S.Assign(name, None, value) =>
      val built = expression(value, context)
      BeAssignVariable(context.assignVariable(name, inferredType(built)), built)
    case S.AssignMember(receiver, member, value) =>
      expression(E.Member(receiver, member), context) match {
        case BeMemberAccess(instance, field) => BeAssignMember(instance, field, expression(value, context))
        case _ => throw IllegalArgumentException(s"Cannot resolve the member assignment to '$member'.")
      }
    case S.If(condition, thenBody, elseBody) =>
      BeIfElse(BeSequence.conditionalBody(List(expression(condition, context))), body(thenBody, context), body(elseBody, context))
    case S.While(condition, loopBody) =>
      BeWhile(BeSequence.conditionalBody(List(expression(condition, context))), body(loopBody, context))
    case S.Repeat(times, loopBody) => BeRepeatNr(expression(times, context), body(loopBody, context))
    case S.For(variable, start, end, loopBody, up) =>
      val (first, last) = (expression(start, context), expression(end, context))
      val counter = context.defineVariable(variable, BeDataType.Numeric)
      BeFor(counter, first, last, body(loopBody, context), if up then BeFor.Direction.Up else BeFor.Direction.Down)
    case S.Return(value) => BeReturn(value.map(expression(_, context)))
    case S.Evaluate(value) => expression(value, context)
    case S.Comment(text) => BeSingleLineComment(LanguageMap.universalMap(text))
    case S.Pass => BeExpression.pass
    case S.Unsupported(source, reason, true) => BeExpressionUnparsable(source, reason, Some(language))
    case S.Unsupported(source, reason, false) => BeExpressionUnsupported(source, Some(language), reason)
  }

  private def buildFunction(definition: S.FunctionDef, context: ParseContext): BeDefineFunction = {
    context.pushScope()
    val built = try {
      val parameters = definition.parameters.map(parameter => context.defineVariable(parameter.name, dataType(parameter.typeHint))).toList
      val signature = BeDefineFunction(parameters, result(definition.returnType), BeExpression.pass,
        BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(definition.name)))
      context.declareFunction(definition.name, signature)
      signature.copy(body = body(definition.body, context))
    } finally context.popScope()
    context.registerFunction(definition.name, built)
    built
  }

  /** A function's result; `None`/`void` and a missing hint both mean that it returns nothing. */
  private def result(returnType: Option[ParsedType]): Option[BeDefineVariable] =
    returnType.filterNot(_ == ParsedType.Unit).map(typeHint => BeDefineVariable(BeEntityName.fromUniversalNameInParts("result"), dataType(typeHint)))

  // Classes: signatures are bound before bodies; definitions never contain a receiver parameter.

  private def kind(definition: S.FunctionDef): BeDefineFunction.BeFunctionType = definition.kind match {
    case ParsedFunctionKind.StaticMethod => StaticMethod()
    case ParsedFunctionKind.Constructor => Constructor()
    case _ => Method()
  }

  private def methodSignature(owner: BeDefineClass, method: S.FunctionDef): BeDefineFunction =
    BeDefineFunction(
      method.parameters.map(parameter => BeDefineVariable(BeEntityName.fromCodeString(parameter.name), dataType(parameter.typeHint))).toList,
      result(method.returnType),
      BeExpression.pass,
      BeFunctionTypeInfo(Some(owner), Option.when(kind(method) != Constructor())(BeEntityName.fromCodeString(method.name)), kind(method)))

  private def fields(definition: S.ClassDef, context: ParseContext): List[BeDefineVariable] =
    definition.fields.map { field =>
      val fieldContext = new ParseContext(context.snapshotStructures, context.sourceCallableNames)
      field.assigned.toSeq.flatMap(_._2).foreach(parameter => fieldContext.defineVariable(parameter.name, dataType(parameter.typeHint)))
      val source = field.initial.orElse(field.assigned.map(_._1))
      lazy val value = source.map(expression(_, fieldContext))
      val constructed = source.collect {
        case E.Call(Seq(name), _) => name
        case E.Construct(name, _) => name
      }.flatMap(context.lookupClass).map(owner => BeDataType.ClassType(owner.name))
      val fieldType = field.typeHint.map(dataType).orElse(constructed).orElse(value.map(inferredType)).getOrElse(BeDataType.AnyType)
      BeDefineVariable(BeEntityName.fromCodeString(field.name), fieldType, if field.initial.nonEmpty then value else None)
    }.groupBy(_.name).values.map(_.head).toList.sortBy(_.name.universalInterpretation())

  private def classSignatures(definition: S.ClassDef, context: ParseContext): BeDefineClass = {
    val owner = BeDefineClass(BeEntityName.fromCodeString(definition.name), fields(definition, context), Nil)
    owner.copy(methods = definition.methods.map(methodSignature(owner, _)).toList)
  }

  private def buildClass(definition: S.ClassDef, context: ParseContext): BeDefineClass = {
    val signatures = classSignatures(definition, context)
    val owner = signatures.copy(methods = Nil)
    val classContext = new ParseContext(context.snapshotStructures, context.sourceCallableNames)
    classContext.registerClass(definition.name, signatures)
    val methods = definition.methods.map { method =>
      val methodContext = new ParseContext(classContext.snapshotStructures, context.sourceCallableNames,
        Option.when(kind(method) != StaticMethod())(owner))
      method.parameters.foreach(parameter => methodContext.defineVariable(parameter.name, dataType(parameter.typeHint)))
      methodSignature(owner, method).copy(body = body(method.body, methodContext))
    }.toList
    val built = owner.copy(methods = methods)
    context.registerClass(definition.name, built)
    built
  }

  // Expressions

  private def expression(parsed: ParsedExpression, context: ParseContext): BeExpression = parsed match {
    case E.Literal(raw) => BeUseValue(BeDataValueLiteral(raw), None)
    case E.NoneValue => BeUseValue(BeDataValueUnit(), None)
    case E.Self => context.currentClass.map(owner => BeCurrentInstance(owner.name))
      .getOrElse(BeExpressionUnsupported(if language.name == "Java" then "this" else "self", Some(language)))
    case E.Name(name) =>
      val variable = context.lookupVariable(name).getOrElse(context.assignVariable(name, BeDataType.AnyType))
      BeUseValue(BeUseValueReference(variable), Some(variable))
    case E.Unary(operator, operand) =>
      val value = expression(operand, context)
      val function = context.resolveOperator(operator, 1, List(value))
      BeFunctionCall.fromDefinition(function, Map(function.inputs.head -> value))
    case E.Binary(operator, left, right) =>
      val (first, second) = (expression(left, context), expression(right, context))
      val function = context.resolveOperator(operator, 2, List(first, second))
      BeFunctionCall.fromDefinition(function, Map(function.inputs.head -> first, function.inputs(1) -> second))
    case E.Call(path, arguments) => call(path, arguments.map(expression(_, context)).toList, context)
    case E.MethodCall(receiver, name, arguments) =>
      instanceCall(expression(receiver, context), name, name, arguments.map(expression(_, context)).toList, context)
    case E.Member(receiver, name) =>
      val instance = expression(receiver, context)
      context.classForType(instance.staticInformationExpression.staticType).flatMap { owner =>
        owner.attributes.find(_.name.universalInterpretation() == BeEntityName.fromCodeString(name).universalInterpretation())
          .map(member => BeMemberAccess(instance, member))
      }.getOrElse(BeExpressionUnsupported(s"${describe(receiver)}.$name", Some(language)))
    case E.Construct(className, arguments) =>
      val owner = context.lookupClass(className)
        .getOrElse(throw IllegalArgumentException(s"Cannot convert construction of unknown class '$className'."))
      bind(className, BeCallTarget.Constructor(constructor(owner)), arguments.map(expression(_, context)).toList)
    case E.Unsupported(source) => BeExpressionUnsupported(source, Some(language))
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
    owner.methods.find(_.functionTypeInfo.funcType == Constructor()).getOrElse(
      BeDefineFunction(Nil, None, BeExpression.pass, BeFunctionTypeInfo(Some(owner.copy(methods = Nil)), None, Constructor())))

  private def findMethod(owner: BeDefineClass, sourceName: String): BeDefineFunction =
    owner.methods.find(_.functionTypeInfo.displayName.universalInterpretation() == BeEntityName.fromCodeString(sourceName).universalInterpretation())
      .getOrElse(throw IllegalArgumentException(s"Class '${owner.name.universalInterpretation()}' has no method '$sourceName'."))

  private def call(path: Seq[String], arguments: List[BeExpression], context: ParseContext): BeExpression = {
    val name = path.mkString(".")
    def isVariable(source: String) = context.lookupVariable(source).nonEmpty
    context.lookupClass(name).filter(_ => !isVariable(name)) match {
      case Some(owner) => bind(name, BeCallTarget.Constructor(constructor(owner)), arguments)
      case None if path.size > 1 =>
        val receiverSource = path.init.mkString(".")
        val methodName = path.last
        context.lookupClass(receiverSource).filter(_ => !isVariable(receiverSource)) match {
          case Some(owner) =>
            val method = findMethod(owner, methodName)
            require(method.functionTypeInfo.funcType == StaticMethod(), s"Instance method '$name' needs an object.")
            bind(name, BeCallTarget.StaticMethod(method), arguments)
          case None if path.head == "self" || path.head == "this" ||
              context.lookupVariable(path.head).exists(variable => context.classForType(variable.variableType).nonEmpty) =>
            val receiverPath = path.init
            val receiver = receiverPath.tail.foldLeft[ParsedExpression](
              if receiverPath.head == "self" || receiverPath.head == "this" then E.Self else E.Name(receiverPath.head))(E.Member(_, _))
            instanceCall(expression(receiver, context), name, methodName, arguments, context)
          case None => moduleFunction(name, arguments, context)
        }
      case None => moduleFunction(name, arguments, context)
    }
  }

  private def instanceCall(instance: BeExpression, name: String, methodName: String, arguments: List[BeExpression], context: ParseContext): BeFunctionCall = {
    val owner = context.classForType(instance.staticInformationExpression.staticType)
      .getOrElse(throw IllegalArgumentException(s"Cannot resolve the receiver of '$name'."))
    val method = findMethod(owner, methodName)
    require(method.functionTypeInfo.funcType == Method(), s"Static method '$name' must be called on its class.")
    bind(name, BeCallTarget.InstanceMethod(instance, method), arguments)
  }

  private def moduleFunction(name: String, arguments: List[BeExpression], context: ParseContext): BeFunctionCall = {
    val function = context.ensureFunctionArity(name, context.resolveFunction(name, arguments.length), arguments.length)
    BeFunctionCall.fromDefinition(function, function.inputs.zip(arguments).toMap)
  }

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
