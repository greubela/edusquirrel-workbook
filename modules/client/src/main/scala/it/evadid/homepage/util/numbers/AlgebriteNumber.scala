package it.evadid.homepage.util.numbers

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.util.control.NonFatal

final class AlgebriteNumber private(private val expr: String, private val canonical: String):

  def +(that: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber(s"(${this.canonical})+(${that.canonical})")

  def -(that: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber(s"(${this.canonical})-(${that.canonical})")

  def *(that: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber(s"(${this.canonical})*(${that.canonical})")

  def /(that: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber(s"(${this.canonical})/(${that.canonical})")

  def unary_- : AlgebriteNumber =
    AlgebriteNumber(s"-(${this.canonical})")

  def pow(n: Int): AlgebriteNumber =
    AlgebriteNumber(s"(${this.canonical})^(${n.toString})")

  def toDouble: Double =
    AlgebriteNumber.parseNumericLiteral(
      AlgebriteNumber.runFloat(canonical)
    )

  def toFiniteDoubleOption: Option[Double] =
    val value = toDouble
    if value.isFinite then Some(value) else None

  def equivalentTo(that: AlgebriteNumber): Boolean =
    AlgebriteNumber.simplifyExpression(s"(${this.canonical})-(${that.canonical})") == "0"

  override def equals(obj: Any): Boolean = {
    obj match {
      case number: AlgebriteNumber => canonical == number.canonical
      case _ => false
    }
  }

  override def hashCode(): Int = canonical.hashCode()

  override def toString: String = canonical

  def toStringWithApprox: String = {
    val approx = maxTwoDigitsString
    val str = toString
    if (approx == str) str
    else "≈" + approx + " (" + str + ")"
  }

  def maxTwoDigitsString: String = {
    val formatStr = ("%.2f").format(toDouble)
    if (formatStr.endsWith("00"))
      formatStr.dropRight(3)
    else formatStr
  }


object AlgebriteNumber:
  private val FiniteNumberPattern =
    """^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$""".r

  def apply(input: String): AlgebriteNumber =
    val canonical = simplifyExpression(input)
    new AlgebriteNumber(input, canonical)

  given Fractional[AlgebriteNumber] with
    def plus(x: AlgebriteNumber, y: AlgebriteNumber): AlgebriteNumber = x + y

    def minus(x: AlgebriteNumber, y: AlgebriteNumber): AlgebriteNumber = x - y

    def times(x: AlgebriteNumber, y: AlgebriteNumber): AlgebriteNumber = x * y

    def div(x: AlgebriteNumber, y: AlgebriteNumber): AlgebriteNumber = x / y

    def negate(x: AlgebriteNumber): AlgebriteNumber = -x

    def fromInt(x: Int): AlgebriteNumber = AlgebriteNumber(x.toString)

    def parseString(str: String): Option[AlgebriteNumber] =
      try {
        val res = Some(AlgebriteNumber(str))
        if(res.isEmpty || res.get.toFiniteDoubleOption.isEmpty) None
        else res
      }
      catch case NonFatal(_) => None

    def toInt(x: AlgebriteNumber): Int = x.toDouble.toInt

    def toLong(x: AlgebriteNumber): Long = x.toDouble.toLong

    def toFloat(x: AlgebriteNumber): Float = x.toDouble.toFloat

    def toDouble(x: AlgebriteNumber): Double = x.toDouble

    def compare(x: AlgebriteNumber, y: AlgebriteNumber): Int =
      val diff = parseNumericLiteral(
        runFloat(s"(${x.canonical})-(${y.canonical})")
      )
      if diff.isNaN then 0
      else if diff < 0 then -1
      else if diff > 0 then 1
      else 0

  private def simplifyExpression(input: String): String =
    val trimmed = input.trim
    val expression = if trimmed.isEmpty then "0" else trimmed
    val result = AlgebriteFacade.run(s"simplify(($expression))").trim
    if result.isEmpty then "0" else result

  private def runFloat(expression: String): String =
    AlgebriteFacade.run(s"float(($expression))").trim

  private def parseNumericLiteral(raw: String): Double = raw match
    case "Infinity" | "+Infinity" => Double.PositiveInfinity
    case "-Infinity" => Double.NegativeInfinity
    case FiniteNumberPattern() => raw.toDouble
    case _ => Double.NaN

@js.native
@JSGlobal("Algebrite")
private object AlgebriteFacade extends js.Object:
  def run(src: String): String = js.native