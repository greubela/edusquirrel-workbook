package it.evadid.vm.conversion.parsed

import it.evadid.vm.parsing.source.SourceSpan

/** Language-neutral source structure produced by the fastparse front ends.
  *
  * Python ([[it.evadid.vm.parsing.python.clean.PythonAstParserSimple]]) and Java
  * ([[it.evadid.vm.parsing.java.clean.JavaParser]]) parse into their own syntax trees; their adapters
  * translate those into this model, keeping raw names, raw literals and declared types. A single
  * [[ParsedProgramBuilder]] then declares every class and function before it builds bodies, so calls
  * can be bound to user definitions and to known names such as the turtle library. Both languages
  * therefore share one set of binding rules.
  */
final case class ParsedProgram(statements: Seq[ParsedStatement])

/** Declared types; the adapters map language spellings (`int`, `double`, `float`, …) onto these. */
enum ParsedType {
  case AnyValue, Numeric, Boolean, Text, Date, Unit
  case Named(name: String)
  case Union(options: Seq[ParsedType])
}

enum ParsedFunctionKind {
  case Function, Method, StaticMethod, Constructor
}

final case class ParsedParameter(name: String, typeHint: Option[ParsedType])

sealed trait ParsedStatement

object ParsedStatement {
  /** `source` is retained so that unsupported classes can be reported with their text. */
  final case class ClassDef(name: String, fields: Seq[Field], methods: Seq[FunctionDef], source: String) extends ParsedStatement

  /** A class attribute. A class-level default has `initial`; an instance field assigned in a method
    * keeps that `assigned` value with the method's parameters, which only determine the field type.
    */
  final case class Field(name: String, typeHint: Option[ParsedType], initial: Option[ParsedExpression],
      assigned: Option[(ParsedExpression, Seq[ParsedParameter])] = None)

  final case class FunctionDef(name: String, parameters: Seq[ParsedParameter], returnType: Option[ParsedType],
      body: Seq[ParsedStatement], kind: ParsedFunctionKind, origin: Option[SourceSpan] = None) extends ParsedStatement

  /** Python `global a, b`: the enclosing function assigns these module variables. */
  final case class Global(names: Seq[String]) extends ParsedStatement

  /** A declaration without a value, such as Python `x: float` or Java `double x;`. */
  final case class Declare(name: String, typeHint: ParsedType) extends ParsedStatement

  final case class Assign(name: String, typeHint: Option[ParsedType], value: ParsedExpression) extends ParsedStatement

  final case class AssignMember(receiver: ParsedExpression, member: String, value: ParsedExpression) extends ParsedStatement

  final case class If(condition: ParsedExpression, thenBody: Seq[ParsedStatement], elseBody: Seq[ParsedStatement]) extends ParsedStatement

  final case class While(condition: ParsedExpression, body: Seq[ParsedStatement]) extends ParsedStatement

  /** A counting loop whose counter is not observable; `times` is any numeric expression. */
  final case class Repeat(times: ParsedExpression, body: Seq[ParsedStatement]) extends ParsedStatement

  /** A counting loop with an inclusive end that counts up (`up`) or down by one and makes no pass when
    * the bounds point the other way, like Python's `range` and Java's counting `for`.
    */
  final case class For(variable: String, start: ParsedExpression, endInclusive: ParsedExpression, body: Seq[ParsedStatement],
      up: Boolean = true) extends ParsedStatement

  /** A loop that runs `update` after each pass, as Java's general `for` and Python's stepped `range`:
    * the initialisation, then a while loop whose body ends with the update. The shared tree has no
    * `continue`, so the update always runs at the end of the body.
    */
  def loopWithUpdate(init: Seq[ParsedStatement], condition: ParsedExpression, body: Seq[ParsedStatement],
      update: Seq[ParsedStatement]): Seq[ParsedStatement] = init :+ While(condition, body ++ update)

  /** A loop over `start, start + step, …` that stops before `stop`, like Python's `range(start, stop, step)`
    * and Java's counting `for`. It becomes the most specific shared loop:
    *
    *  - a [[Repeat]] when `counterObserved` is false and both bounds are whole-number literals, or the
    *    loop counts from 0 by 1 to a computed bound (`range(n)`);
    *  - a [[For]] counting up or down for steps of ±1 (literal bounds that describe no pass are an empty repeat);
    *  - otherwise the counter's initialisation and a while loop that ends with the step.
    *
    * The caller decides whether the counter is observed: Java counters live only in the loop, while
    * a Python counter remains visible after it.
    */
  def countingLoop(variable: String, start: ParsedExpression, stop: ParsedExpression, step: Int,
      body: Seq[ParsedStatement], counterObserved: Boolean): Seq[ParsedStatement] = {
    require(step != 0, "A counting loop needs a nonzero step.")
    val passes = (ParsedExpression.wholeNumber(start), ParsedExpression.wholeNumber(stop)) match {
      case (Some(first), Some(last)) => Some(math.max(0, if step > 0 then (last - first + step - 1) / step else (first - last - step - 1) / -step))
      case _ => None
    }
    passes match {
      case Some(times) if !counterObserved || times == 0 => Seq(Repeat(ParsedExpression.Literal(times.toString), body))
      // `range(n)` with an unobserved counter repeats n times, also when n is computed.
      case None if !counterObserved && step == 1 && ParsedExpression.wholeNumber(start).contains(0) => Seq(Repeat(stop, body))
      case _ if step.abs == 1 => Seq(For(variable, start, ParsedExpression.offset(stop, -step), body, up = step > 0))
      case _ =>
        val (comparison, change) = if step > 0 then ("<", "+") else (">", "-")
        loopWithUpdate(Seq(Assign(variable, None, start)), ParsedExpression.Binary(comparison, ParsedExpression.Name(variable), stop), body,
          Seq(Assign(variable, None, ParsedExpression.Binary(change, ParsedExpression.Name(variable), ParsedExpression.Literal(step.abs.toString)))))
    }
  }

  final case class Return(value: Option[ParsedExpression]) extends ParsedStatement

  final case class Evaluate(expression: ParsedExpression) extends ParsedStatement

  final case class Comment(text: String) extends ParsedStatement

  case object Pass extends ParsedStatement

  /** Recognised syntax the shared tree cannot represent (`unparsable = false`) or broken syntax. */
  final case class Unsupported(source: String, reason: String, unparsable: Boolean, origin: Option[SourceSpan] = None) extends ParsedStatement
}

sealed trait ParsedExpression

object ParsedExpression {
  /** Numbers, quoted strings and `True`/`False`, spelled as the shared tree stores them. */
  final case class Literal(raw: String) extends ParsedExpression

  case object NoneValue extends ParsedExpression

  final case class Name(name: String) extends ParsedExpression

  /** Python `self`, Java `this`. */
  case object Self extends ParsedExpression

  /** Operators use the shared symbols: `and`, `or`, `not`, `==`, `//`, `**`, … */
  final case class Unary(operator: String, operand: ParsedExpression) extends ParsedExpression

  final case class Binary(operator: String, left: ParsedExpression, right: ParsedExpression) extends ParsedExpression

  /** A call by (possibly qualified) name: `forward(…)`, `turtle.forward(…)`, `Walker.make()`, `w.walk(…)`. */
  final case class Call(path: Seq[String], arguments: Seq[ParsedExpression]) extends ParsedExpression

  /** A call on a computed receiver, e.g. `self.pen.down()`. */
  final case class MethodCall(receiver: ParsedExpression, name: String, arguments: Seq[ParsedExpression]) extends ParsedExpression

  final case class Member(receiver: ParsedExpression, name: String) extends ParsedExpression

  /** Explicit construction, Java `new Walker(…)`; Python constructs by calling the class. */
  final case class Construct(className: String, arguments: Seq[ParsedExpression]) extends ParsedExpression

  final case class Unsupported(source: String, origin: Option[SourceSpan] = None) extends ParsedExpression

  /** The value of a whole-number literal, possibly negated. */
  def wholeNumber(expression: ParsedExpression): Option[Int] = expression match {
    case Literal(raw) => raw.toIntOption
    case Unary("-", operand) => wholeNumber(operand).map(-_)
    case Unary("+", operand) => wholeNumber(operand)
    case _ => None
  }

  /** A whole-number literal, negated as the front ends spell negative numbers. */
  def wholeNumberLiteral(value: Int): ParsedExpression =
    if value < 0 then Unary("-", Literal((-value).toString)) else Literal(value.toString)

  /** `expression + amount`, folding literals and cancelling a trailing `± n` (`n + 1` - 1 is `n`). */
  def offset(expression: ParsedExpression, amount: Int): ParsedExpression = {
    def plus(base: ParsedExpression, value: Int): ParsedExpression =
      if value == 0 then base else if value > 0 then Binary("+", base, Literal(value.toString)) else Binary("-", base, Literal((-value).toString))
    (expression, wholeNumber(expression)) match {
      case (_, Some(value)) => wholeNumberLiteral(value + amount)
      case (Binary("+", base, Literal(raw)), _) if raw.toIntOption.nonEmpty => plus(base, raw.toInt + amount)
      case (Binary("-", base, Literal(raw)), _) if raw.toIntOption.nonEmpty => plus(base, amount - raw.toInt)
      case _ => plus(expression, amount)
    }
  }
}
