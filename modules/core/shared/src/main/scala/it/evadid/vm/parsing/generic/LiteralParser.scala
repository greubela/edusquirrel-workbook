package it.evadid.vm.parsing.generic

import fastparse.*
import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.python.clean.PyAST.{PyAtomar, PyLiteral}
import it.evadid.vm.parsing.python.clean.PythonType
import fastparse.NoWhitespace._
object LiteralParser {

  def enrichLiteral[T](literal: String, pythonType: PythonType, serializer: Serializer[T]): PyLiteral[T] = PyLiteral(literal, pythonType, serializer.deserialize(literal), serializer)


  def decinteger[ctx: P]: P[PyLiteral[BigInt]] =
    P((nonzero_digit ~~ (P("_").? ~~ digit).rep | P("0").rep(1, sep = P("_").?)).!)
      .map(str => enrichLiteral(str, PythonType.PYTHON_INTEGER, Serializer.intDecimalIO))

  def bininteger[ctx: P]: P[PyLiteral[BigInt]] = P("0" ~~ CharIn("bB") ~~ (P("_").? ~~ bindigit).rep(1)).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_INTEGER, Serializer.intBinaryIO))

  def octinteger[ctx: P]: P[PyLiteral[BigInt]] = P("0" ~~ CharIn("oO") ~~ (P("_").? ~~ octdigit).rep(1)).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_INTEGER, Serializer.intOctalIO))

  def hexinteger[ctx: P]: P[PyLiteral[BigInt]] = P("0" ~~ CharIn("xX") ~~ (P("_").? ~~ hexdigit).rep(1)).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_INTEGER, Serializer.intHexIO))

  def INTEGER[ctx: P]: P[PyLiteral[BigInt]] = P(hexinteger | octinteger | bininteger | decinteger)

  def pointfloat[ctx: P]: P[String] = P(digitpart.? ~~ fraction | digitpart ~~ ".").!

  def expfloat[ctx: P]: P[String] = P((digitpart | pointfloat) ~~ exponent).!

  def FLOAT_NUMBER[ctx: P]: P[PyLiteral[Double]] = P(expfloat | pointfloat).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_INTEGER, Serializer.floatIO))

  // def IMAG_NUMBER[ctx: P]: P[String] = P((FLOAT_NUMBER | digitpart) ~~ CharIn("jJ")).!

  def NUMBER[ctx: P]: P[PyLiteral[?]] = P(FLOAT_NUMBER | INTEGER)

  def FALSE[ctx: P]: P[PyLiteral[Boolean]] = P("False" ~~ !ID_CONTINUE).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_BOOL, Serializer.pythonBooleanIO))

  def TRUE[ctx: P]: P[PyLiteral[Boolean]] = P("True" ~~ !ID_CONTINUE).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_BOOL, Serializer.pythonBooleanIO))


  def literal[ctx: P]: P[PyLiteral[?]] =   NUMBER | TRUE | FALSE | STRING_LITERAL

  def STRING_LITERAL[ctx: P]: P[PyLiteral[String]] = P(STRING).!
    .map(str => enrichLiteral(str, PythonType.PYTHON_STRING, Serializer.stringIO))


}
