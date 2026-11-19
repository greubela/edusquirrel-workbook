package it.evadid.vm.conversion.expressionjava

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeIntrinsic
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.types.BeDataType

/** How Java spells the shared conversion built-ins (Python's `int`, `float`, `str`, `bool`, `abs`).
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
    Seq("Math", "abs") -> "abs")

  /** The built-in a primitive cast means: `(int)` truncates like `int`, `(double)` converts like `float`. */
  def cast(javaTypeName: String): Option[String] = javaTypeName match {
    case "int" | "long" | "short" | "byte" => Some("int")
    case "double" | "float" => Some("float")
    case _ => None
  }

  def handles(intrinsic: BeIntrinsic): Boolean = intrinsic match {
    case BeIntrinsic.ToInteger | BeIntrinsic.ToNumber | BeIntrinsic.ToText | BeIntrinsic.ToBoolean | BeIntrinsic.Absolute => true
    case _ => false
  }

  /** The Java expression for a built-in call; `argument` and `operand` are the printed argument, the
    * latter parenthesised where a cast or operator needs it.
    */
  def spell(intrinsic: BeIntrinsic, value: BeExpression, argument: String, operand: String): String = {
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
      case other => throw IllegalArgumentException(s"$other is not a conversion built-in.")
    }
  }
}
