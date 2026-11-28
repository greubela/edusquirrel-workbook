package it.evadid.vm.conversion.unittest

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.interactions.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.workbook.interactions.programming.state.snap.SnapScalaModel
import munit.FunSuite

class TypedSnapExecutionSpec extends FunSuite {
  test("unit-test conversion reads semantic nodes directly, without reparsing lexical XML metadata") {
    val xml = """<project><stage><sprites><sprite><scripts><script><block s="receiveGo"/><block s="forward"><l>10</l></block></script></scripts></sprite></sprites></stage></project>"""
    val document = SourceParsing.parse(xml, SourceLanguage.Snap).syntax.toOption.get
    val expected = ProgramForUnitTestExecution.fromState(ProgrammingStateSnapScalaModel(document))
    val root = document.root.asInstanceOf[SnapScalaModel.Project]
    val lexicalChange = document.copy(nodes = Vector(root.copy(element = root.element.copy(closing = "</different>"))))
    val typed = ProgrammingStateSnapScalaModel(lexicalChange)
    assert(SourceParsing.parse(typed.toSnapXml.code, SourceLanguage.Snap).syntax.isLeft)
    assertEquals(ProgramForUnitTestExecution.fromState(typed), expected)
    assert(expected.isRight)
  }
}
