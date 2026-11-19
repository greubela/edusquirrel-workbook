package it.evadid.vm.conversion.expressionjava

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeIntrinsic
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.types.BeDataType

/** How Java spells the shared built-ins (Python's `int`, `float`, `str`, `bool`, `abs`, `max`, `min`).
  *
  * The printer chooses a spelling from the argument's static type: numbers are cast, text is
  * parsed. The Java adapter reads each spelling back as the built-in, so one table serves both
  * directions. `bool` has no Java conversion of its own; numbers compare with zero, which reads
  * back as that comparison.
  */
object JavaBuiltinSpelling {
  /** Static calls the printer emits, by qualified Java name, with the built-in they mean. */
  val calls: Map[Seq[String], String] = Map(
    Seq("Integer", "parseInt") -> "int",
    Seq("Double", "parseDouble") -> "float",
    Seq("String", "valueOf") -> "str",
    Seq("Math", "abs") -> "abs",
    Seq("Math", "max") -> "max",
    Seq("Math", "min") -> "min")

  /** The built-in a primitive cast means: `(int)` truncates like `int`, `(double)` converts like `float`. */
  def cast(javaTypeName: String): Option[String] = javaTypeName match {
    case "int" | "long" | "short" | "byte" => Some("int")
    case "double" | "float" => Some("float")
    case _ => None
  }

  def handles(intrinsic: BeIntrinsic): Boolean = intrinsic match {
    case BeIntrinsic.ToInteger | BeIntrinsic.ToNumber | BeIntrinsic.ToText | BeIntrinsic.ToBoolean | BeIntrinsic.Absolute |
        BeIntrinsic.Maximum | BeIntrinsic.Minimum => true
    case _ => false
  }

  /** The Java expression for a built-in call. Each argument comes with its printed value and, for
    * casts and operators, its parenthesised operand form.
    */
  final case class Argument(value: BeExpression, printed: String, operand: String)

  def spell(intrinsic: BeIntrinsic, arguments: Seq[Argument]): String = (intrinsic, arguments) match {
    case (BeIntrinsic.Maximum, Seq(first, second)) => s"Math.max(${first.printed}, ${second.printed})"
    case (BeIntrinsic.Minimum, Seq(first, second)) => s"Math.min(${first.printed}, ${second.printed})"
    case (_, Seq(Argument(value, argument, operand))) =>
      val valueType = value.staticInformationExpression.staticType
      val isText = valueType == BeDataType.String
      intrinsic match {
        case BeIntrinsic.ToInteger => if isText then s"Integer.parseInt($argument)" else s"(int) $operand"
        case BeIntrinsic.ToNumber => if isText then s"Double.parseDouble($argument)" else s"(double) $operand"
        case BeIntrinsic.ToText => s"String.valueOf($argument)"
        case BeIntrinsic.Absolute => s"Math.abs($argument)"
        case BeIntrinsic.ToBoolean if valueType == BeDataType.Boolean => argument
        case BeIntrinsic.ToBoolean if valueType == BeDataType.Numeric => s"($operand != 0)"
        case BeIntrinsic.ToBoolean => throw ProgrammingConversionException.unsupported(
          "Java has no truth value for this bool(...) argument. Compare it explicitly, for example with != 0.")
        case other => throw IllegalArgumentException(s"$other is not a Java built-in.")
      }
    case _ => throw ProgrammingConversionException.unsupported(s"$intrinsic takes a different number of values in Java.")
  }
}
