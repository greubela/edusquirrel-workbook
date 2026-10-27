package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

class ProgrammingLanguageConversionSpec extends FunSuite {
  test("language routing chooses the requested representation rather than always Snap") {
    val python = ProgrammingStatePythonString("forward(12)\nright(90)\n")
    val sources = List[ProgrammingState](python, python.toJava, python.toSnapXml,
      python.toSnapXml.toSnapScalaModel.toOption.get)
    sources.foreach { source =>
      assertEquals(source.toLanguage(Python), source.toPython)
      assertEquals(source.toLanguage(Java), source.toJava)
      assertEquals(source.toLanguage(SnapLanguage), source.toSnapXml)
      assertEquals(source.toLanguage(Python).associatedLanguage, Python)
      assertEquals(source.toLanguage(Java).associatedLanguage, Java)
      assertEquals(source.toLanguage(SnapLanguage).associatedLanguage, SnapLanguage)
    }
  }
  test("matching languages preserve incomplete source without invoking another parser") {
    val java = ProgrammingStateJavaString("\r\n class Drawing {\t")
    val python = ProgrammingStatePythonString("\n def incomplete(\t")
    val snap = ProgrammingStateSnapXml("unfinished project", List("opaque metadata"))
    assertEquals(java.toLanguage(Java), java)
    assertEquals(python.toLanguage(Python), python)
    assertEquals(snap.toLanguage(SnapLanguage), snap)
  }
  test("semantic expression tasks retain their native block view") {
    val source = ProgrammingStatePythonString("forward(12)").toBeExpression
    assertEquals(source.associatedLanguage, SnapLanguage)
    assert(source.toLanguage(SnapLanguage).isInstanceOf[ProgrammingStateSnapXml])
  }
  test("unsupported language conversion is explicit") {
    intercept[UnsupportedOperationException](ProgrammingStatePythonString("forward(12)").toLanguage(SQL))
  }
}
