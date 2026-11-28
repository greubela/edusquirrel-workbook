package it.evadid.homepage.webElements.editor.code.JavaEditor

import it.evadid.homepage.webElements.editor.code.JavaEditor.JavaEditorNavigation.{Mode, Position}
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.structure.JavaUmlDiagram
import it.evadid.workbook.interactions.programming.state.ProgrammingStateJavaStructured
import munit.FunSuite

/** The navigation transitions of the Java component editor, without any DOM. */
class JavaEditorNavigationSpec extends FunSuite {
  private val program = ProgrammingStateJavaStructured(
    "class Main {\n  static void main(String[] args) {\n    draw();\n  }\n\n  static void draw() {}\n}\n\nclass Helper {\n  int size = 3;\n}")
  private val diagram = JavaUmlDiagram(program)
  private def key(className: String, function: Option[String] = None) =
    diagram.key(BeEntityName.fromCodeString(className), function).get
  private val main = key("Main", Some("static void main(String[] args) {\n    draw();\n  }"))
  private val helper = key("Helper")

  test("a single main function opens in Function Mode, otherwise the full source") {
    assertEquals(Position.initial(program), Position(Mode.Function, Some(main)))
    assertEquals(Position.initial(ProgrammingStateJavaStructured("class A { void f() {} }")), Position(Mode.Full, None))
  }

  test("choosing an entry picks its mode; switching modes picks a matching entry") {
    val start = Position(Mode.Full, None)
    assertEquals(start.choose(helper), Position(Mode.Class, Some(helper)))
    assertEquals(start.choose(main), Position(Mode.Function, Some(main)))
    assertEquals(start.switchTo(Mode.Class, diagram), Position(Mode.Class, Some(key("Main"))))
    assertEquals(Position(Mode.Class, Some(key("Main"))).switchTo(Mode.Function, diagram).selection, Some(main),
      "the selected class's first function")
    assertEquals(Position(Mode.Class, Some(helper)).switchTo(Mode.Function, diagram).selection, Some(helper),
      "a class without functions stays selected")
    assertEquals(Position(Mode.Function, Some(main)).switchTo(Mode.Full, diagram), Position(Mode.Full, Some(main)))
  }

  test("a draft holding several declarations widens the open editor's mode") {
    val function = Position(Mode.Function, Some(main))
    assertEquals(function.widen(Mode.Function, Scope.Owner, diagram), Position(Mode.Class, Some(key("Main"))))
    assertEquals(function.widen(Mode.Function, Scope.Program, diagram), Position(Mode.Full, Some(main)))
    assertEquals(Position(Mode.Class, Some(helper)).widen(Mode.Class, Scope.Program, diagram), Position(Mode.Full, Some(helper)))
    assertEquals(Position(Mode.Class, Some(helper)).widen(Mode.Function, Scope.Program, diagram), Position(Mode.Class, Some(helper)),
      "an editor that is not shown does not widen")
  }

  test("an added entry is revealed; an edited function stays selected only in Function Mode") {
    assertEquals(Position(Mode.Full, None).reveal(diagram, BeEntityName.fromCodeString("Helper"), None), Position(Mode.Class, Some(helper)))
    val edited = ProgrammingStateJavaStructured(program.toString.replace("static void draw() {}", "static void draw() { forward(1); }"))
    val function = JavaFunctionEditor.find(edited, BeEntityName.fromCodeString("Main"), "static void draw() { forward(1); }").get
    val followed = Position(Mode.Function, Some(main)).follow(function, edited)
    assertEquals(followed.selection.flatMap(_.member).map(_.name), Some("draw"))
    assertEquals(Position(Mode.Class, Some(helper)).follow(function, edited), Position(Mode.Class, Some(helper)))
    assertEquals(Position(Mode.Function, Some(main)).follow(function, program), Position(Mode.Function, Some(main)),
      "a method not found in the answer never falls back to its class")
  }
}
