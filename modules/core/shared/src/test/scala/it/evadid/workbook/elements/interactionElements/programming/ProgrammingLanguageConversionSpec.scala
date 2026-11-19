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
      assertEquals(source.convertToLanguage(Python).finalState.get, source.toPythonStructured)
      assertEquals(source.convertToLanguage(Java).finalState.get, source.toJavaStructured)
      assertEquals(source.convertToLanguage(SnapLanguage).finalState.get, source.toSnapXml.toSnapScalaModel.toOption.get.persistent)
      assertEquals(source.convertToLanguage(Python).finalState.get.associatedLanguage, Python)
      assertEquals(source.convertToLanguage(Java).finalState.get.associatedLanguage, Java)
      assertEquals(source.convertToLanguage(SnapLanguage).finalState.get.associatedLanguage, SnapLanguage)
    }
  }
  test("matching languages preserve incomplete source without invoking another parser") {
    val java = ProgrammingStateJavaString("\r\n class Drawing {\t")
    val python = ProgrammingStatePythonString("\n def incomplete(\t")
    val snap = ProgrammingStateSnapXml("unfinished project")
    assert(java.convertToLanguage(Java).finalState.get.toString.contains("class Drawing"))
    assert(python.convertToLanguage(Python).finalState.get.toString.contains("def incomplete("))
    assert(snap.convertToLanguage(SnapLanguage).finalState.isEmpty)

  }
  test("semantic expression tasks retain their native block view") {
    val source = ProgrammingStatePythonString("forward(12)").toBeExpression
    assertEquals(source.associatedLanguage, SnapLanguage)
    assert(source.convertToLanguage(SnapLanguage).finalState.get.isInstanceOf[ProgrammingStateSnapScalaModel])
  }
  test("unsupported language conversion is explicit") {
    assert(ProgrammingStatePythonString("forward(12)").convertToLanguage(SQL).finalState.isEmpty)
  }
}
