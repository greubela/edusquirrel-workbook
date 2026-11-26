package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateRaw, ProgrammingStateSnapScalaModel}
import munit.FunSuite

class SnapSourceSpec extends FunSuite {
  private val xml = """<project><scripts><script x="10" y="20"><block s="receiveGo"/><block s="forward"><l>25</l></block></script></scripts></project>"""

  test("lexical inspection retains every character of incomplete and invalid XML") {
    Seq("", "not XML", "<", "<project", "<project name='unfinished>", "<!-- open", "<![CDATA[open",
      "<?open", "<!DOCTYPE project [", "<project></wrong>").foreach { text =>
      val source = SourceParsing.parse(text, SourceLanguage.Snap)
      val tokens = source.structured.tokens
      assertEquals(tokens.map(token => text.substring(token.start, token.end)).mkString, text)
      assertEquals(tokens.map(_.start), (0 +: tokens.map(_.end)).dropRight(1))
      assert(source.syntax.isLeft)
      assertEquals(source.structured.source, text, "syntax failure must not replace the lexical stage")
    }
    val unfinished = SourceParsing.parse("<project name='unfinished>", SourceLanguage.Snap).structured
    assert(!unfinished.tokens.last.closed)
  }

  test("quoted delimiters, comments, CDATA and DTDs keep their spelling without resolving entities") {
    val text = """<?xml version='1.0'?><!DOCTYPE project SYSTEM "https://invalid.example/no-fetch"><project name = 'a&gt;b'><notes><![CDATA[<raw>]]><!-- > --></notes><extension value=">"/></project >"""
    val internal = """<!DOCTYPE project [<!ENTITY marker "] and > and [">]><project/>"""
    for value <- Seq(text, internal) do {
      val source = SourceParsing.parse(value, SourceLanguage.Snap)
      assert(source.structured.tokens.forall(_.closed))
      assertEquals(source.syntax.toOption.get.toXml, value)
      assert(source.structured.tokens.exists(_.kind == SnapSourceStructured.Kind.Declaration))
    }
  }

  test("later validation rejects duplicate attributes, invalid entities and mismatched tags at their position") {
    Seq(
      "<project a='1' a='2'/>" -> ("one attribute named a", 20),
      "<project>\n&#x110000;</project>" -> ("a valid character reference", 10),
      "<project>\n</other>" -> ("closing tag </project>", 10),
      "<project>\n  <a b='<'/></project>" -> ("closing ' of the attribute value", 18)
    ).foreach { case (text, (expected, offset)) =>
      val source = SourceParsing.parse(text, SourceLanguage.Snap)
      assert(source.structured.tokens.nonEmpty)
      val failure = source.syntax.swap.toOption.get
      assert(failure.message.contains(expected), clue = (text, failure))
      assertEquals(failure.offset, offset, clue = (text, failure))
      assertEquals(failure.line, SourceLines(text).line(offset))
    }
  }

  test("unreadable XML fails every later step with a located conversion error, empty text is an empty project") {
    val source = SourceParsing.parse("<project>\n<scenes>", SourceLanguage.Snap)
    assertEquals(source.syntax.swap.toOption.map(_.line), Some(2))
    assert(source.project.isLeft && source.unsupportedSelectors.isLeft && source.parsed.isLeft)
    assertEquals(source.project.swap.toOption.flatMap(_.error.line), Some(2))
    assertEquals(SourceParsing.parse("", SourceLanguage.Snap).unsupportedSelectors, Right(Nil))
  }

  test("all consumers reuse the same source and completed stages") {
    val source = SourceParsing.parse(xml, SourceLanguage.Snap)
    assert(source eq SourceParsing.parse(xml, SourceLanguage.Snap))
    assert(source.syntax eq source.syntax)
    assert(source.project eq source.project)
    assert(source.parsed eq source.parsed)
  }

  test("copied block sequences become one script without altering the source document") {
    val text = """<block s="forward"><l>10</l></block><block s="turn"><l>90</l></block>"""
    val source = SourceParsing.parse(text, SourceLanguage.Snap)
    assert(source.syntax.isLeft, "a document still requires one root")
    assertEquals(source.project.map(_.allBlocks.size), Right(2))
    assertEquals(source.structured.source, text)
  }

  test("large embedded assets stay with their source owner rather than the global cache") {
    val text = "<project><thumbnail>" + "x" * (512 * 1024) + "</thumbnail></project>"
    val first = SourceParsing.parse(text, SourceLanguage.Snap)
    assert(!(first eq SourceParsing.parse(text, SourceLanguage.Snap)))
    assert(first.syntax eq first.syntax)
    assertEquals(first.syntax.toOption.get.toXml, text)
  }

  test("raw Snap and typed documents keep the same behavior through both text languages") {
    val raw = ProgrammingStateRaw(SnapLanguage, xml)
    val model = ProgrammingStateSnapScalaModel(SourceParsing.parse(xml, SourceLanguage.Snap).syntax.toOption.get)
    val expected = raw.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(expected.map(_.args.head), List(25.0))
    assertEquals(model.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected)
    for state <- Seq(raw, model); language <- Seq(Java, Python) do {
      val converted = state.convertToLanguage(language).finalState.get
      val restored = converted.toSnapXml.toBeExpression
      assertEquals(restored.turtleCommandExecutionInfo.completedCommands, expected)
    }
  }
}
