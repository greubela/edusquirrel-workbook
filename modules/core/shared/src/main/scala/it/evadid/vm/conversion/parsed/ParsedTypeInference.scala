package it.evadid.vm.conversion.parsed

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

/** Fills in the types an untyped source leaves out, before the shared tree is built, so that typed targets
  * such as Java receive compilable signatures.
  *
  *  - An unannotated parameter of a function takes the one type its evidence agrees on: the arguments
  *    passed to it, and its own direct uses as an argument of a typed function or an operand of arithmetic.
  *  - A function that returns a value without declaring a result takes the one type its returned values
  *    share, otherwise `AnyValue`; it never stays a function returning nothing.
  *
  * Annotations in the source are kept; conflicting or missing evidence leaves a parameter untyped.
  */
object ParsedTypeInference {
  private val arithmetic = Set("+", "-", "*", "/", "//", "%", "**")
  private val logic = Set("<", "<=", ">", ">=", "==", "!=", "and", "or", "&&", "||", "not", "!")

  def infer(program: ParsedProgram): ParsedProgram = {
    // Signatures depend on each other through calls: rounds settle chains of helper functions before the
    // remaining results fall back to `AnyValue`.
    val settled = Iterator.iterate(program)(round(_, fallback = false)).sliding(2).collectFirst { case Seq(a, b) if a == b => a }
    round(settled.getOrElse(program), fallback = true)
  }

  private def round(program: ParsedProgram, fallback: Boolean): ParsedProgram = {
    val functions = definitions(program.statements).map(definition => definition.name -> definition).toMap
    val calls = callsIn(program.statements)
    ParsedProgram(program.statements.map(rewrite(_, functions, calls, fallback)))
  }

  private def rewrite(statement: ParsedStatement, functions: Map[String, S.FunctionDef], calls: Seq[E.Call],
      fallback: Boolean): ParsedStatement =
    statement match {
      case definition: S.FunctionDef if definition.kind == ParsedFunctionKind.Function =>
        val parameters = definition.parameters.zipWithIndex.map { (parameter, index) =>
          parameter.typeHint.fold(parameter.copy(typeHint = parameterType(definition, parameter.name, index, functions, calls)))(_ => parameter)
        }
        val typed = definition.copy(parameters = parameters, body = definition.body.map(rewrite(_, functions, calls, fallback)))
        if typed.returnType.nonEmpty || !returnsValue(typed.body) then typed
        else returnedType(typed, functions).orElse(Option.when(fallback)(ParsedType.AnyValue))
          .fold(typed)(result => typed.copy(returnType = Some(result)))
      case other => mapBodies(other, rewrite(_, functions, calls, fallback))
    }

  private def parameterType(definition: S.FunctionDef, name: String, index: Int, functions: Map[String, S.FunctionDef],
      calls: Seq[E.Call]): Option[ParsedType] = {
    val passed = calls.collect { case E.Call(Seq(`definition`.name), arguments) if arguments.size > index => arguments(index) }
      .map(typeOf(_, Map.empty, functions))
    val used = expressionsIn(definition.body).flatMap(usesAs(name, _, functions))
    agreed(passed ++ used)
  }

  /** The types a direct use of `name` requires: an argument of a typed function, or an arithmetic operand. */
  private def usesAs(name: String, expression: ParsedExpression, functions: Map[String, S.FunctionDef]): Seq[Option[ParsedType]] =
    expression match {
      case E.Call(Seq(callee), arguments) =>
        arguments.zipWithIndex.collect { case (E.Name(`name`), index) => argumentType(callee, index, functions) }
      case E.Binary(operator, left, right) if arithmetic(operator) && operator != "+" =>
        Seq(left, right).collect { case E.Name(`name`) => Some(ParsedType.Numeric) }
      case E.Binary("+", E.Name(`name`), other) => Seq(typeOf(other, Map.empty, functions).filter(_ == ParsedType.Numeric))
      case E.Binary("+", other, E.Name(`name`)) => Seq(typeOf(other, Map.empty, functions).filter(_ == ParsedType.Numeric))
      case _ => Nil
    }

  private def argumentType(callee: String, index: Int, functions: Map[String, S.FunctionDef]): Option[ParsedType] =
    functions.get(callee).flatMap(_.parameters.lift(index).flatMap(_.typeHint))
      .orElse(known(callee).flatMap(_.inputs.lift(index)).flatMap(input => parsedType(input.variableType)))

  private def known(name: String): Option[BeDefineFunction] =
    TurtleCommand.resolveTurtleMethod(name)
      .orElse(DefaultDefinitions.builtinFunctionDefinitions.collectFirst { case (`name`, definition) => definition })

  private def returnedType(definition: S.FunctionDef, functions: Map[String, S.FunctionDef]): Option[ParsedType] = {
    val locals = definition.parameters.flatMap(parameter => parameter.typeHint.map(parameter.name -> _)).toMap
    agreed(returns(definition.body).map(typeOf(_, locals, functions)))
  }

  /** The type of an expression when it is evident from its form alone. */
  private def typeOf(expression: ParsedExpression, locals: Map[String, ParsedType], functions: Map[String, S.FunctionDef]): Option[ParsedType] =
    expression match {
      case E.Literal(raw) => parsedType(BeUseValue(BeDataValueLiteral(raw), None).staticInformationExpression.staticType)
      case E.Name(name) => locals.get(name)
      case E.Unary(operator, _) if logic(operator) => Some(ParsedType.Boolean)
      case E.Unary("-", operand) => typeOf(operand, locals, functions).filter(_ == ParsedType.Numeric)
      case E.Binary(operator, _, _) if logic(operator) => Some(ParsedType.Boolean)
      case E.Binary(operator, left, right) if arithmetic(operator) =>
        (typeOf(left, locals, functions), typeOf(right, locals, functions)) match {
          case (Some(ParsedType.Numeric), Some(ParsedType.Numeric)) => Some(ParsedType.Numeric)
          case (Some(ParsedType.Text), _) | (_, Some(ParsedType.Text)) if operator == "+" => Some(ParsedType.Text)
          case _ => None
        }
      case E.Call(Seq(callee), _) =>
        functions.get(callee).flatMap(_.returnType).filterNot(_ == ParsedType.AnyValue)
          .orElse(known(callee).flatMap(_.outputs).flatMap(output => parsedType(output.variableType)))
      case _ => None
    }

  private def parsedType(dataType: BeDataType): Option[ParsedType] =
    if dataType == BeDataType.Numeric || dataType == BeDataType.Int then Some(ParsedType.Numeric)
    else if dataType == BeDataType.Boolean then Some(ParsedType.Boolean)
    else if dataType == BeDataType.String then Some(ParsedType.Text)
    else None

  /** The single type all known evidence agrees on; unknown evidence is ignored, conflicting evidence is none. */
  private def agreed(evidence: Seq[Option[ParsedType]]): Option[ParsedType] = evidence.flatten.distinct match {
    case Seq(single) => Some(single)
    case _ => None
  }

  private def definitions(statements: Seq[ParsedStatement]): Seq[S.FunctionDef] = statements.flatMap {
    case definition: S.FunctionDef => definition +: definitions(definition.body)
    case other => bodies(other).flatMap(definitions)
  }

  private def returnsValue(statements: Seq[ParsedStatement]): Boolean = returns(statements).nonEmpty

  /** Values returned by this body, not counting nested definitions. */
  private def returns(statements: Seq[ParsedStatement]): Seq[ParsedExpression] = statements.flatMap {
    case S.Return(Some(value)) => Seq(value)
    case _: S.FunctionDef | _: S.ClassDef => Nil
    case other => bodies(other).flatMap(returns)
  }

  private def callsIn(statements: Seq[ParsedStatement]): Seq[E.Call] =
    expressionsIn(statements, nested = true).flatMap(subexpressions).collect { case call: E.Call => call }

  /** The expressions of these statements; with `nested`, also those inside nested definitions and classes. */
  private def expressionsIn(statements: Seq[ParsedStatement], nested: Boolean = false): Seq[ParsedExpression] = statements.flatMap {
    case S.Assign(_, _, value) => subexpressions(value)
    case S.AssignMember(receiver, _, value) => subexpressions(receiver) ++ subexpressions(value)
    case S.If(condition, thenBody, elseBody) => subexpressions(condition) ++ expressionsIn(thenBody, nested) ++ expressionsIn(elseBody, nested)
    case S.While(condition, body) => subexpressions(condition) ++ expressionsIn(body, nested)
    case S.Repeat(times, body) => subexpressions(times) ++ expressionsIn(body, nested)
    case S.For(_, start, end, body, _) => subexpressions(start) ++ subexpressions(end) ++ expressionsIn(body, nested)
    case S.Return(value) => value.toSeq.flatMap(subexpressions)
    case S.Evaluate(value) => subexpressions(value)
    case definition: S.FunctionDef if nested => expressionsIn(definition.body, nested)
    case definition: S.ClassDef if nested => definition.methods.flatMap(method => expressionsIn(method.body, nested))
    case _ => Nil
  }

  private def subexpressions(expression: ParsedExpression): Seq[ParsedExpression] = expression +: (expression match {
    case E.Unary(_, operand) => subexpressions(operand)
    case E.Binary(_, left, right) => subexpressions(left) ++ subexpressions(right)
    case E.Call(_, arguments) => arguments.flatMap(subexpressions)
    case E.MethodCall(receiver, _, arguments) => subexpressions(receiver) ++ arguments.flatMap(subexpressions)
    case E.Member(receiver, _) => subexpressions(receiver)
    case E.Construct(_, arguments) => arguments.flatMap(subexpressions)
    case _ => Nil
  })

  private def bodies(statement: ParsedStatement): Seq[Seq[ParsedStatement]] = statement match {
    case S.If(_, thenBody, elseBody) => Seq(thenBody, elseBody)
    case S.While(_, body) => Seq(body)
    case S.Repeat(_, body) => Seq(body)
    case S.For(_, _, _, body, _) => Seq(body)
    case _ => Nil
  }

  private def mapBodies(statement: ParsedStatement, update: ParsedStatement => ParsedStatement): ParsedStatement = statement match {
    case branch: S.If => branch.copy(thenBody = branch.thenBody.map(update), elseBody = branch.elseBody.map(update))
    case loop: S.While => loop.copy(body = loop.body.map(update))
    case loop: S.Repeat => loop.copy(body = loop.body.map(update))
    case loop: S.For => loop.copy(body = loop.body.map(update))
    case other => other
  }
}
