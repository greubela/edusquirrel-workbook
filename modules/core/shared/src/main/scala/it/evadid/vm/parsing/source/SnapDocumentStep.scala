package it.evadid.vm.parsing.source

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*

/** Step 2: a fastparse XML grammar that keeps every node's lexical spelling (attribute spacing, quotes, closing
  * tags). DTDs are retained as text and never resolved or fetched.
  */
private[source] object SnapDocumentStep {
  private val maxDepth = 256
  private val maxNodes = 1000000

  def apply(structured: SnapSourceStructured): Either[SyntaxFailure, Vector[Node]] = {
    val xml = structured.source
    def failure(message: String, offset: Int) = {
      val lines = SourceLines(xml)
      Left(SyntaxFailure(message, offset, lines.line(offset), lines.column(offset)))
    }
    structured.tokens.find(!_.closed) match {
      case Some(token) => failure(s"Unterminated ${token.kind}", token.start)
      case None => fastparse.parse(xml, document(using _)) match {
        case Parsed.Success(nodes, _) if count(nodes) > maxNodes => failure("Too many XML nodes", 0)
        case Parsed.Success(nodes, _) => Right(nodes)
        case stopped: Parsed.Failure => failure(s"Expected ${stopped.trace().label}", stopped.index)
      }
    }
  }

  private def count(nodes: Vector[Node]): Int = nodes.foldLeft(nodes.size) {
    case (total, element: SnapElement) => total + count(element.element.children)
    case (total, _) => total
  }

  /** Numeric character references must name an XML character; other `&` text stays opaque (DTD entities). */
  private def allowed(code: Long): Boolean = code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff)
  private def numeral[$: P]: P[Long] = P(
    "x" ~ CharsWhileIn("0-9a-fA-F").!.map(hex => if hex.length > 8 then Long.MaxValue else java.lang.Long.parseLong(hex, 16)) |
      CharsWhileIn("0-9").!.map(digits => if digits.length > 10 then Long.MaxValue else digits.toLong))
  private def invalidReference[$: P]: P[Unit] = P(("&#" ~ numeral ~ ";").filter(!allowed(_))).map(_ => ())
  private def reference[$: P]: P[Unit] = P(&(invalidReference) ~/ Fail.opaque("a valid character reference"))
  /** Characters up to one that `stops`, with character references checked where they start. */
  private def characters[$: P](stops: Char => Boolean): P[Unit] =
    P((reference | CharsWhile(c => !stops(c) && c != '&', 1) | "&").rep(1))

  private def space[$: P]: P[String] = P(CharsWhile(_.isWhitespace, 0).!)
  private def name[$: P]: P[String] =
    P((CharPred(c => c.isLetter || c == '_' || c == ':') ~ CharsWhile(c => c.isLetterOrDigit || "_:-.".contains(c), 0)).!)
      .opaque("an XML name")

  /** Text up to `close`, scanning runs without its first character at once. */
  private def until[$: P](close: String): P[String] =
    P((CharsWhile(_ != close.head, 1) | !close ~ AnyChar).rep.!)

  private def comment[$: P]: P[Node] = P("<!--" ~/ until("-->") ~ "-->").map(XmlComment(_))
  private def cdata[$: P]: P[Node] = P("<![CDATA[" ~/ until("]]>") ~ "]]>").map(CData(_))
  private def instruction[$: P]: P[Node] = P("<?" ~/ until("?>") ~ "?>").map(ProcessingInstruction(_))
  private def quoted[$: P]: P[Unit] = P("\"" ~ CharsWhile(_ != '"', 0) ~ "\"" | "'" ~ CharsWhile(_ != '\'', 0) ~ "'")
  private def subset[$: P]: P[Unit] = P("[" ~ (quoted | subset | CharsWhile(c => !"[]'\"".contains(c), 1)).rep ~ "]")
  private def declaration[$: P]: P[Node] =
    P(("<!DOCTYPE" ~/ (quoted | subset | CharsWhile(c => !"[>'\"".contains(c), 1)).rep ~ ">").!).map(Declaration(_))
  private def text[$: P]: P[Node] = P(characters(_ == '<').!).map(Text(_))

  private def quotedValue[$: P](quote: String): P[(String, String)] =
    P(quote.! ~/ characters(c => c == quote.head || c == '<').?.! ~
      P(quote).opaque(s"the closing $quote of the attribute value (values cannot contain '<')"))
  private def value[$: P]: P[(String, String)] = P(quotedValue("\"") | quotedValue("'"))
  private def attribute[$: P]: P[Attribute] =
    P(CharsWhile(_.isWhitespace, 1).! ~ name ~ space ~ "=" ~ space ~ value)
      .map { case (leading, attributeName, before, after, (quote, raw)) => Attribute(attributeName, raw, leading, before, after, quote) }
  private def unique[$: P](attributes: Seq[Attribute]): P[Vector[Attribute]] =
    attributes.map(_.name).diff(attributes.map(_.name).distinct).headOption
      .fold(Pass(attributes.toVector))(duplicate => Fail.opaque(s"one attribute named $duplicate"))

  private def element[$: P](depth: Int): P[Node] =
    if depth > maxDepth then Fail.opaque(s"nesting within $maxDepth levels")
    else P("<" ~ name ~/ attribute.rep.flatMap(unique(_)) ~ space ~ ("/>".!.map(_ => true) | ">".!.map(_ => false))).flatMap {
      case (tag, attributes, trailing, true) => Pass(classify(Element(tag, attributes, trailing = trailing, selfClosing = true)))
      case (tag, attributes, trailing, false) =>
        P(node(depth + 1).rep ~ closing(tag)).map((children, close) =>
          classify(Element(tag, attributes, children.toVector, trailing, selfClosing = false, close)))
    }

  private def closing[$: P](tag: String): P[String] =
    P(("</" ~ name.filter(_ == tag) ~ space ~ ">").!).opaque(s"closing tag </$tag>")

  private def node[$: P](depth: Int): P[Node] = P(comment | cdata | instruction | declaration | element(depth) | text)
  private def document[$: P]: P[Vector[Node]] = P(node(0).rep ~ End).map(_.toVector)
}
