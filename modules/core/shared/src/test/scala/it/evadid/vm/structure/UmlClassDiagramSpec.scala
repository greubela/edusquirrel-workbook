package it.evadid.vm.structure

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.core.datastructures.uml.{UmlClassDiagram, UmlClassOrigin}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.workbook.interactions.programming.state.ProgrammingStateJavaStructured
import munit.FunSuite
import upickle.default.*

class UmlClassDiagramSpec extends FunSuite {
  test("converted turtle programs expose their library class in the reusable UML model") {
    val source = ProgrammingStateRaw(Python, "import turtle\nturtle.forward(20)\n").toJavaStructured
    val diagram = JavaUmlDiagram(source)
    val turtle = diagram.classes.find(_.name == "Turtle").getOrElse(fail(source.toString))
    assert(turtle.functions.exists(_.name == "forward"))
  }

  test("a verbatim library definition is a read-only library box; an edited copy is the program's own class") {
    val source = ProgrammingStateRaw(Python, "forward(10)").toJava.code
    def origins(code: String) = JavaUmlDiagram(ProgrammingStateJavaStructured(code)).classes.map(value => value.name -> value.origin)
    assertEquals(origins(source), Seq("Main" -> UmlClassOrigin.Program, "Turtle" -> UmlClassOrigin.Library))
    assertEquals(origins(source.replace("move(distance);", "move(2 * distance);")),
      Seq("Main" -> UmlClassOrigin.Program, "Turtle" -> UmlClassOrigin.Program))
    assertEquals(JavaUmlDiagram(ProgrammingStateJavaStructured(source)).editable.classes.map(_.name), Seq("Main"))
  }

  test("libraries and unknown dependencies a program uses become read-only boxes in their Java form") {
    val program = ProgrammingStateJavaStructured(
      "class Main { static void main(String[] a) { System.out.println(1); System.out2.println(2); Object limit = Config.screen.limit; Helper.run(4, 5); } }")
    val analysis = ProgramAnalysis.of(program)
    val external = JavaUmlDiagram.external(analysis.libraryDependencies, analysis.unknownDependency)
    def box(name: String) = external.classes.find(_.name == name).getOrElse(fail(external.classes.map(_.name).toString))
    assertEquals(box("PrintStream").origin, UmlClassOrigin.Library)
    assertEquals(box("PrintStream").functions.map(_.label), Seq("+print(value: Object): void", "+println(value: Object): void"))
    // java.lang's `System` is recognized; the program's `out2` is implied by its use but defined nowhere.
    assertEquals(box("System").origin, UmlClassOrigin.Library)
    assertEquals(box("System").attributes.map(member => member.label -> member.implied),
      Seq("+out: PrintStream" -> false, "+err: PrintStream" -> false, "+out2: SystemOut2" -> true))
    assert(box("System").attributes.forall(_.isStatic))
    assertEquals(box("SystemOut2").origin, UmlClassOrigin.Unknown)
    assert(Seq("SystemOut2", "Helper", "Config", "ConfigScreen").flatMap(box(_).members).forall(_.implied))
    assertEquals(box("SystemOut2").functions.map(_.label), Seq("+println(arg0)"))
    assert(!box("SystemOut2").functions.exists(_.isStatic))
    assertEquals(box("Helper").functions.map(_.label), Seq("+run(arg0, arg1)"))
    assertEquals(box("Config").attributes.map(_.label), Seq("+screen: ConfigScreen"))
    assertEquals(box("ConfigScreen").attributes.map(_.label), Seq("+limit"))
    assert(external.classes.forall(!_.origin.editable))
    val diagram = JavaUmlDiagram(program).including(external)
    assertEquals(diagram.classes.head.name, "Main")
    assertEquals(diagram.editable.classes.map(_.name), Seq("Main"))
  }

  test("a program without unknown uses shows only recognized library boxes, without java.lang's System") {
    val analysis = ProgramAnalysis.of(ProgrammingStateRaw(Python, "forward(10)").toJavaStructured)
    val external = JavaUmlDiagram.external(analysis.libraryDependencies, analysis.unknownDependency)
    assertEquals(external.classes.map(value => value.name -> value.origin), Seq("Turtle" -> UmlClassOrigin.Library))
    assert(external.classes.flatMap(_.members).forall(!_.implied))
  }

  test("Python shows the modules it uses read-only, recognized members apart from implied ones") {
    val program = ProgrammingStateRaw(Python, "import turtle\nturtle.forward(10)\nprint('x')\nturtle.spin(2)\nfoo.bar(3)\n")
    val analysis = ProgramAnalysis.of(program)
    val external = PythonUmlDiagram.external(analysis.libraryDependencies, analysis.unknownDependency)
    def box(name: String) = external.classes.find(_.name == name).getOrElse(fail(external.classes.map(_.name).toString))
    assertEquals(external.classes.map(value => value.name -> value.origin),
      Seq("turtle" -> UmlClassOrigin.Library, "builtins" -> UmlClassOrigin.Library, "foo" -> UmlClassOrigin.Unknown))
    val turtle = box("turtle").functions
    assert(turtle.exists(member => member.label == "+forward(distance)" && !member.implied && member.isStatic), turtle.toString)
    assert(turtle.exists(_.name == "penup"), "Python's spelling of the turtle commands")
    assertEquals(turtle.filter(_.implied).map(_.label), Seq("+spin(arg0)"))
    assertEquals(box("builtins").functions.map(_.label), Seq("+print(*values, file=None)"))
    assertEquals(box("foo").functions.map(member => member.label -> member.implied), Seq("+bar(arg0)" -> true))
    assertEquals(turtle.map(_.key).distinct.size, turtle.size)

    val diagram = PythonUmlDiagram(program.toPythonStructured).including(external)
    assertEquals(diagram.editable.classes.map(_.name), Seq("__main__"))
  }
}
