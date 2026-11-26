package it.evadid.vm.types

/** A string literal as [[BeDataValueLiteral]] holds it: quoted source text (`"Ada"` or Python's `'Ada'`).
  * Printers that write a string differently (Snap slots, Java) use its [[content]].
  */
object StringLiteral {
  /** `text` as a double-quoted literal; its escapes are valid in Python and Java alike. */
  def quote(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

  /** Whether `literal` is a single-line quoted string. */
  def isQuoted(literal: String): Boolean =
    literal.length >= 2 && (literal.head == '"' || literal.head == '\'') && literal.last == literal.head &&
      !(literal.length >= 6 && literal.startsWith(literal.take(3)) && literal.take(3).forall(_ == literal.head))

  /** The text of a quoted literal with its escapes decoded, the inverse of [[quote]]; numbers and other
    * literals are their source text.
    */
  def content(literal: String): String =
    if !isQuoted(literal) then literal
    else escapeSequence.replaceAllIn(literal.substring(1, literal.length - 1), escape => scala.util.matching.Regex.quoteReplacement(
      escape.group(1) match {
        case "n" => "\n"
        case "r" => "\r"
        case "t" => "\t"
        case unicode if unicode.length == 5 => Integer.parseInt(unicode.tail, 16).toChar.toString
        case other => other
      }))

  private val escapeSequence = """(?s)\\(u[0-9a-fA-F]{4}|.)""".r
}
