package it.evadid.util.parsing

import fastparse._




object Js {
  sealed trait Val extends Any {
    def value: Any
    def apply(i: Int): Val = this.asInstanceOf[Arr].value(i)
    def apply(s: java.lang.String): Val =
      this.asInstanceOf[Obj].value.find(_._1 == s).get._2
  }
  case class Str(value: java.lang.String) extends AnyVal with Val
  case class Obj(value: (java.lang.String, Val)*) extends AnyVal with Val
  case class Arr(value: Val*) extends AnyVal with Val
  case class Num(value: Double) extends AnyVal with Val
  case object False extends Val{
    def value = false
  }
  case object True extends Val{
    def value = true
  }
  case object Null extends Val{
    def value = null
  }
}

object JsonGrammar{
  import fastparse._, NoWhitespace._
  def stringChars(c: Char) = c != '\"' && c != '\\'

  def space[$: P]         = P( CharsWhileIn(" \r\n", 0) )
  def digits[$: P]        = P( CharsWhileIn("0-9") )
  def exponent[$: P]      = P( CharIn("eE") ~ CharIn("+\\-").? ~ digits )
  def fractional[$: P]    = P( "." ~ digits )
  def integral[$: P]      = P( "0" | CharIn("1-9")  ~ digits.? )

  def number[$: P] = P(  CharIn("+\\-").? ~ integral ~ fractional.? ~ exponent.? ).!.map(
    x => Js.Num(x.toDouble)
  )

  def `null`[$: P]        = P( "null" ).map(_ => Js.Null)
  def `false`[$: P]       = P( "false" ).map(_ => Js.False)
  def `true`[$: P]        = P( "true" ).map(_ => Js.True)

  def hexDigit[$: P]      = P( CharIn("0-9a-fA-F") )
  def unicodeEscape[$: P] = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
  def escape[$: P]        = P( "\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape) )

  def strChars[$: P] = P( CharsWhile(stringChars) )
  def string[$: P] =
    P( space ~ "\"" ~/ (strChars | escape).rep.! ~ "\"").map(Js.Str.apply)

  def array[$: P] =
    P( "[" ~/ jsonExpr.rep(sep=","./) ~ space ~ "]").map(Js.Arr(_:_*))

  def pair[$: P] = P( string.map(_.value) ~/ ":" ~/ jsonExpr )

  def obj[$: P] =
    P( "{" ~/ pair.rep(sep=","./) ~ space ~ "}").map(Js.Obj(_:_*))

  def jsonExpr[$: P]: P[Js.Val] = P(
    space ~ (obj | array | string | `true` | `false` | `null` | number) ~ space
  )
}
