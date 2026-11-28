package it.evadid.workbook.interactions.programming

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.state.*
import it.evadid.workbook.interactions.programming.state.snap.*
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.*
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.*

import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingExercise
import munit.FunSuite

class ProgrammingStateSnapXmlCleanupSpec extends FunSuite {
  test("remove generated images across scenes while preserving exact program XML") {
    val scripts = """<scripts><script x="70" y="80"><block s="forward"><l>10</l></block></script></scripts>"""
    val expected = s"<project><notes>keep</notes><scenes><scene><stage>$scripts</stage></scene><scene><stage></stage></scene></scenes></project>"
    val bloated = s"<project><thumbnail>data:image/png;base64,preview</thumbnail><notes>keep</notes><scenes><scene><stage><pentrails>data:image/png;base64,trails</pentrails>$scripts</stage></scene><scene><stage><pentrails id=\"2\">other</pentrails></stage></scene></scenes></project>"
    assertEquals(ProgrammingStateRaw(SnapLanguage, bloated).removeBloatFromXml.code, expected)
  }

  test("remove self-closing, nested and legacy singular image nodes") {
    val xml = "<project><thumbnail/><stage><pentrails><thumbnail>nested</thumbnail></pentrails><pentrail id=\"legacy\"/></stage><thumbnail-extra>keep</thumbnail-extra></project>"
    assertEquals(ProgrammingStateRaw(SnapLanguage, xml).removeBloatFromXml.code,
      "<project><stage></stage><thumbnail-extra>keep</thumbnail-extra></project>")
  }

  test("cleaning is idempotent and clean states retain their identity") {
    val state = ProgrammingStateRaw(SnapLanguage, "<project><thumbnail>image</thumbnail><scripts/></project>")
    val clean = state.removeBloatFromXml
    assertEquals(clean.removeBloatFromXml, clean)
    assert(clean.removeBloatFromXml eq clean)
    assertEquals(state.code, "<project><thumbnail>image</thumbnail><scripts/></project>")
  }

  test("preserve authored costumes, sound assets, data URLs in literals and escaped text") {
    val xml = """<project><costumes><costume name="Turtle" image="data:image/png;base64,authored"/></costumes><sounds><sound sound="data:audio/wav;base64,authored"/></sounds><scripts><l>data:image/png;base64,literal &lt;thumbnail&gt;text&lt;/thumbnail&gt;</l></scripts></project>"""
    assertEquals(ProgrammingStateRaw(SnapLanguage, xml).removeBloatFromXml.code, xml)
  }

  test("image-like markup inside comments, CDATA and processing instructions remains text") {
    val text = """<?note <thumbnail>keep</thumbnail> ?><!-- <pentrails>keep</pentrails> --><l><![CDATA[<thumbnail>literal</thumbnail>]]></l>"""
    val state = ProgrammingStateRaw(SnapLanguage, s"<project>$text<thumbnail>remove</thumbnail></project>")
    assertEquals(state.removeBloatFromXml.code, s"<project>$text</project>")
  }

  test("empty and incomplete XML are handled without truncating the remaining source") {
    for (xml <- List("", "<project>", "<project><thumbnail>incomplete", "<project><!-- <thumbnail>literal"))
      assertEquals(ProgrammingStateRaw(SnapLanguage, xml).removeBloatFromXml.code, xml)
  }

  test("large regenerated image payloads are excluded from the serialized cleaned state") {
    val payload = "A" * (1024 * 1024)
    val clean = ProgrammingStateRaw(SnapLanguage, s"<project><thumbnail>$payload</thumbnail><stage><pentrails>$payload</pentrails><scripts/></stage></project>").removeBloatFromXml
    assertEquals(clean.code, "<project><stage><scripts/></stage></project>")
    val serialized = ProgrammingState.serializer.serialize(clean)
    assert(serialized.length < 200)
    assertEquals(ProgrammingState.serializer.deserialize(serialized), clean)
  }

  test("canonical Snap projects retain custom definitions and canvas layout byte for byte") {
    val original = ProgrammingStateSnapScalaModel.mini.toSnapXml
    val bloated = original.copy(code = original.code.replace("</project>", "<thumbnail>preview</thumbnail></project>"))
    assertEquals(bloated.removeBloatFromXml, original)
    assertEquals(bloated.removeBloatFromXml.toPython, original.toPython)
  }

}
