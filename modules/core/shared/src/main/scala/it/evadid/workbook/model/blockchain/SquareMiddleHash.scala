package it.evadid.workbook.model.blockchain

/** Worksheet Hashq, not a cryptographic hash: retain two middle decimal digits, biased left for odd lengths. */
case class SquareMiddleHashResult(input: BigInt, square: BigInt, start: Int, hash: String) {
  def highlightedSquare: String = {
    val digits = square.toString
    digits.take(start) + "[" + hash + "]" + digits.drop(start + 2)
  }
}
object SquareMiddleHash {
  val maxInputDigits: Int = 100
  def calculate(input: BigInt): SquareMiddleHashResult = {
    require(input > 3, "Worksheet input must be greater than 3")
    val square = input * input
    val digits = square.toString
    val start = (digits.length - 2) / 2
    SquareMiddleHashResult(input, square, start, digits.substring(start, start + 2))
  }
  def parse(input: String): Option[SquareMiddleHashResult] = {
    val digits = input.trim
    if digits.matches(s"[0-9]{1,$maxInputDigits}") then {
      val value = BigInt(digits)
      Option.when(value > 3)(value).map(calculate)
    } else None
  }
}
