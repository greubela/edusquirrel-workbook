package it.evadid.vm.editing

import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.structure.PythonUmlDiagram
import it.evadid.workbook.interactions.programming.state.ProgrammingStatePythonStructured
import munit.FunSuite

class PythonMethodEditingSpec extends FunSuite {
  test("method edits retain their class indentation and leave the next method untouched") {
    val code = "class Box:\n    def f(self):\n        return 1\n\n    def g(self):\n        return 2\n"
    val method = PythonMethodEditing.functions(code, false).head
    val edited = code.take(method.span.start) + PythonMethodEditing.indented("def f(self):\n    return 3", method.span.text(code)) +
      code.drop(method.span.end)
    assert(edited.contains("    def f(self):\n        return 3\n"))
    assert(edited.endsWith("    def g(self):\n        return 2\n"))
    assert(PythonMethodEditing.valid(edited))
  }

  test("module helpers are declared before main is called; empty classes accept new methods") {
    val code = "import turtle\ndef main():\n    pass\nmain()\n"
    val edited = PythonMethodEditing.insert(code, "def step():\n    turtle.forward(2)", true)
    assert(edited.indexOf("def step") < edited.indexOf("def main"))
    assert(edited.startsWith("import turtle\n"))
    val owner = PythonMethodEditing.insert("class Box:\n    pass\n", "def f(self):\n    pass", false)
    assert(PythonMethodEditing.valid(owner))
    assertEquals(PythonMethodEditing.functions(owner, false).map(_.name), Vector("f"))
  }

  test("dialog declarations reject keywords and incomplete syntax but accept Unicode names") {
    assert(PythonMethodEditing.validName("Größe"))
    assert(!PythonMethodEditing.validName("class"))
    assert(!PythonMethodEditing.validName("1step"))
    assert(!PythonMethodEditing.valid("def f():\n    x ="))
    assert(!PythonMethodEditing.valid("def f(class):\n    pass"))
  }

  test("Python classes and module functions populate the shared UML model") {
    val state = ProgrammingStatePythonStructured("def main():\n    pass\nmain()\nclass Box:\n    count: int = 2\n    def __init__(self, value: float):\n        self.value = value\n    @staticmethod\n    def f(n: float) -> float:\n        return n\n")
    val diagram = PythonUmlDiagram(state)
    assertEquals(diagram.classes.head.functions.map(_.name), Seq("main"))
    val box = diagram.classes.find(_.name == "Box").get
    assertEquals(box.attributes.map(_.name).toSet, Set("count", "value"))
    assert(box.attributes.find(_.name == "count").get.isStatic)
    assert(!box.attributes.find(_.name == "value").get.isStatic)
    assert(box.functions.find(_.name == "f").get.isStatic)
    val renamed = PythonMethodEditing.updated(state, BeEntityName.fromCodeString("Box"), "class Renamed:\n    pass\n")
    assert(!renamed.definedClasses.contains(BeEntityName.fromCodeString("Box")))
    assert(renamed.definedClasses.contains(BeEntityName.fromCodeString("Renamed")))
  }
}
