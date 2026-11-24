package it.evadid.vm.structure

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import it.evadid.core.datastructures.uml.UmlClassDiagram
import munit.FunSuite
import upickle.default.*

class UmlClassDiagramSpec extends FunSuite {
  test("converted turtle programs expose their library class in the reusable UML model") {
    val source = ProgrammingStateRaw(Python, "import turtle\nturtle.forward(20)\n").toJavaStructured
    val diagram = JavaUmlDiagram(source)
    val turtle = diagram.classes.find(_.name == "Turtle").getOrElse(fail(source.toString))
    assert(turtle.functions.exists(_.name == "forward"))
    assertEquals(read[UmlClassDiagram](write(diagram)), diagram)
  }
}
