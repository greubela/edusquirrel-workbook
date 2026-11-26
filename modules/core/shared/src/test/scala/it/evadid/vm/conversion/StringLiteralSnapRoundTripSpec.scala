package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** Snap slots hold a string's text, not its source literal: a route through Snap must neither show the quotes in
  * the slot nor add them to the string's content when converting back.
  */
class StringLiteralSnapRoundTripSpec extends FunSuite {
  private def converted(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState =
    {
      val result = state.convertToLanguage(language)
      result.finalState.getOrElse(fail(s"no $language state for\n$state\n${result.conversionInfo}"))
    }

  /** Through Snap and back, also through the XML the Snap editor stores. */
  private def throughSnap(source: ProgrammingState): (String, Seq[ProgrammingState]) = {
    val snap = converted(source, SnapLanguage)
    val xml = snap.toSnapXml.code
    (xml, Seq(converted(snap, source.associatedLanguage), converted(ProgrammingStateRaw(SnapLanguage, xml), source.associatedLanguage)))
  }

  private val texts = Seq("Ada", "hello world", "!", "say \"hi\"", "it's", "back\\slash")

  test("Python strings keep their content through Snap") {
    val source = ProgrammingStateRaw(Python, "name = \"Ada\"\nprint(\"hello world\")\nprint(name + \"!\")\n" +
      "print(\"say \\\"hi\\\"\")\nprint(\"it's\")\nprint(\"back\\\\slash\")\nprint(\"Hello \" + name)\n")
    val (xml, back) = throughSnap(source)
    for (text <- Seq("Ada", "hello world", "!", "say &quot;hi&quot;", "it&apos;s", "back\\slash", "Hello "))
      assert(xml.contains(s"<l>$text</l>") || xml.contains(s"<l>${text.replace("&apos;", "'")}</l>"), s"slot <l>$text</l> in\n$xml")
    assert(!xml.contains("<l>&quot;"), s"a slot starts with a quote:\n$xml")
    for (state <- back) {
      val python = state.toPythonStructured.toString
      assert(python.contains("\"Ada\"") && python.contains("print(\"hello world\")") && python.contains("\"!\""), python)
      assert(python.contains("print(\"say \\\"hi\\\"\")") && python.contains("print(\"it's\")") && python.contains("print(\"back\\\\slash\")"), python)
      assert(python.contains("\"Hello \" + "), s"spaces belong to the string:\n$python")
      assert(!python.contains("\"\\\"Ada"), python)
      assertEquals(converted(converted(state, SnapLanguage), Python).toPythonStructured.toString, python, "a second route changes nothing")
    }
  }

  test("Java strings keep their content through Snap") {
    val source = ProgrammingStateRaw(Java, "public class Main {\n  public static void main(String[] args) {\n    String name = \"Ada\";\n" +
      "    System.out.println(\"hello world\");\n    System.out.println(name + \"!\");\n    System.out.println(\"say \\\"hi\\\"\");\n  }\n}\n")
    val (xml, back) = throughSnap(source)
    for (text <- Seq("Ada", "hello world", "!", "say &quot;hi&quot;")) assert(xml.contains(s"<l>$text</l>"), s"slot <l>$text</l> in\n$xml")
    for (state <- back) {
      val java = state.toJavaStructured.toString
      assert(java.contains("\"Ada\"") && java.contains("println(\"hello world\")") && java.contains("\"!\"") &&
        java.contains("println(\"say \\\"hi\\\"\")"), java)
      assert(!java.contains("\"\\\"Ada"), java)
    }
  }

  test("a slot shows a literal's decoded text") {
    import it.evadid.vm.types.StringLiteral.{quote, content}
    for (text <- texts :+ "tab\tand\nline" :+ "") assertEquals(content(quote(text)), text)
    assertEquals(content("'single'"), "single")
    assertEquals(content("\"\\u00e9\""), "é")
    assertEquals(content("42"), "42")
    assertEquals(content("\""), "\"")
  }
}
