package it.evadid.vm.conversion.parsed

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
      body: Seq[ParsedStatement], kind: ParsedFunctionKind) extends ParsedStatement

  /** A declaration without a value, such as Python `x: float` or Java `double x;`. */
  final case class Declare(name: String, typeHint: ParsedType) extends ParsedStatement

  final case class Assign(name: String, typeHint: Option[ParsedType], value: ParsedExpression) extends ParsedStatement

  final case class AssignMember(receiver: ParsedExpression, member: String, value: ParsedExpression) extends ParsedStatement

  final case class If(condition: ParsedExpression, thenBody: Seq[ParsedStatement], elseBody: Seq[ParsedStatement]) extends ParsedStatement

  final case class While(condition: ParsedExpression, body: Seq[ParsedStatement]) extends ParsedStatement

  /** A counting loop whose counter is not observable. */
  final case class Repeat(times: Int, body: Seq[ParsedStatement]) extends ParsedStatement

  /** A counting loop with an inclusive end, as Snap's `for` block counts. */
  final case class For(variable: String, start: ParsedExpression, endInclusive: ParsedExpression, body: Seq[ParsedStatement]) extends ParsedStatement

  final case class Return(value: Option[ParsedExpression]) extends ParsedStatement

  final case class Evaluate(expression: ParsedExpression) extends ParsedStatement

  final case class Comment(text: String) extends ParsedStatement

  case object Pass extends ParsedStatement

  /** Recognised syntax the shared tree cannot represent (`unparsable = false`) or broken syntax. */
  final case class Unsupported(source: String, reason: String, unparsable: Boolean) extends ParsedStatement
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

  final case class Unsupported(source: String) extends ParsedExpression
}
