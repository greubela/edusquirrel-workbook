package it.evadid.homepage.webElements.code

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.conversion.structured.SourceStructure.{Param, Visibility}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStatePythonStructured, ProgrammingStateRaw}

class PythonStructureSpec extends FunSuite {
  import JavaMethodSource.*

  private val dialect = StructureDialect.PythonDialect
  private def analysis(code: String) = JavaMethodSource(code, dialect)
  private def module(code: String) = analysis(code).classes.head

  private val walker =
    """import turtle
      |
      |class Walker(Base):
      |    unit: float = 5.0
      |    # def commented(self): pass
      |
      |    def __init__(self, step: float, name):
      |        self.step = step
      |        self._name: str = name
      |        self.__count = 0
      |
      |    def walk(self, times: float) -> float:
      |        text = "def fake():"
      |        turtle.forward(self.step * times)
      |        return times
      |
      |    @staticmethod
      |    def make() -> "Walker":
      |        return Walker(1, "w")
      |
      |
      |class Base:
      |    pass
      |
      |
      |def helper(size: float,
      |           count: int = 2):
      |    turtle.left(90)
      |
      |
      |def main():
      |    w = Walker(15, "a")
      |    helper(w.walk(2))
      |
      |
      |main()
      |""".stripMargin

  test("module functions form the module box, classes their methods, constructor and attributes") {
    val current = analysis(walker)
    assertEquals(current.classes.map(_.name), List("", "Walker", "Base"))
    val main = current.classes.head
    assert(main.isModule)
    assertEquals(main.methods.map(_.name), List("helper", "main"))
    assertEquals(main.methods.head.params, List(Param("float", "size"), Param("int", "count")))
    assertEquals(walker.substring(main.methods.head.start, main.methods.head.end),
      "def helper(size: float,\n           count: int = 2):\n    turtle.left(90)")
    val walkerClass = current.classes(1)
    assertEquals(walkerClass.superclasses, List("Base"))
    assertEquals(walkerClass.methods.map(method => (method.name, method.isConstructor, method.isStatic, method.returnType)),
      List(("__init__", true, false, ""), ("walk", false, false, "float"), ("make", false, true, "\"Walker\"")))
    assertEquals(walkerClass.methods.head.params, List(Param("float", "step"), Param("", "name")))
    assertEquals(walkerClass.fields.map(field => (field.name, field.javaType, field.isStatic, field.visibility)), List(
      ("unit", "float", true, Visibility.Public), ("step", "float", false, Visibility.Public),
      ("_name", "str", false, Visibility.Protected), ("__count", "int", false, Visibility.Private)))
    assertEquals(walker.substring(walkerClass.methods(2).start, walkerClass.methods(2).end),
      "@staticmethod\n    def make() -> \"Walker\":\n        return Walker(1, \"w\")")
    assert(current.relations.contains(Relation("Walker", "Base", RelationKind.Inheritance)), current.relations)
    assert(current.relations.contains(Relation("", "Walker", RelationKind.Dependency)), current.relations)
    assertEquals(current.initialMethod(requirePublicMain = false).map(_.name), Some("main"))
  }

  test("UML texts of Python members omit a None result and a missing type") {
    val init = analysis(walker).classes(1).methods.head
    assertEquals(umlOperation(init), "+ __init__(step: float, name)")
    val field = JavaField("Walker", Visibility.Public, isStatic = false, isFinal = false, "", "speed", None, 0, 0, shared = false)
    assertEquals(umlAttribute(field), "+ speed")
    assertEquals(umlOperation(module(walker).methods(1)), "+ main()")
  }

  test("a new class goes after the last class, a new function after the last function, without -> None") {
    val withClass = dialect.addClass(walker, "Pen")
    assert(withClass.contains("class Base:\n    pass\n\nclass Pen:\n    pass\n\n\ndef helper"), withClass)
    val declaration = dialect.methodDeclaration(module(walker), "None", "square", List(Param("float", "size")), isStatic = false, Visibility.Public)
    assertEquals(declaration, "def square(size: float):\n    pass")
    val withFunction = dialect.addMethod(walker, module(walker), declaration)
    assert(withFunction.contains("    helper(w.walk(2))\n\ndef square(size: float):\n    pass\n\n\nmain()"), withFunction)
    assertEquals(module(withFunction).methods.map(_.name), List("helper", "main", "square"))
  }

  test("methods replace a lone pass, static methods get the decorator and no self") {
    val base = analysis(walker).classes(2)
    val declaration = dialect.methodDeclaration(base, "float", "area", Nil, isStatic = true, Visibility.Public)
    assertEquals(declaration, "@staticmethod\ndef area() -> float:\n    pass")
    val added = dialect.addMethod(walker, base, declaration)
    assert(added.contains("class Base:\n    @staticmethod\n    def area() -> float:\n        pass\n"), added)
    val method = dialect.methodDeclaration(base, "None", dialect.memberName("turn", Visibility.Private), List(Param("", "angle")),
      isStatic = false, Visibility.Private)
    assertEquals(method, "def __turn(self, angle):\n    pass")
  }

  test("attributes: instance attributes join __init__, class attributes the class level, a new __init__ is created") {
    val walkerClass = analysis(walker).classes(1)
    val instance = dialect.fieldDeclaration(Visibility.Public, isStatic = false, "bool", "visible", "")
    assertEquals(instance, "self.visible: bool = False")
    val withInstance = dialect.saveField(walker, walkerClass, None, isStatic = false, instance)
    assert(withInstance.contains("        self.__count = 0\n        self.visible: bool = False\n"), withInstance)
    val shared = dialect.fieldDeclaration(Visibility.Public, isStatic = true, "int", "limit", "3")
    val withShared = dialect.saveField(walker, walkerClass, None, isStatic = true, shared)
    assert(withShared.contains("    unit: float = 5.0\n    limit: int = 3\n"), withShared)
    val base = analysis(walker).classes(2)
    val withInit = dialect.saveField(walker, base, None, isStatic = false, dialect.fieldDeclaration(Visibility.Public, false, "float", "size", ""))
    assert(withInit.contains("class Base:\n    def __init__(self):\n        self.size: float = 0.0\n"), withInit)
    assertEquals(analysis(withInit).classes(2).fields.map(_.name), List("size"))
  }

  test("a constructor stores its parameters and sits before the first method") {
    val base = analysis(walker).classes(2)
    val declaration = dialect.constructorDeclaration(base, Visibility.Public, List(Param("float", "x")), dialect.assignedParameters(base, List(Param("float", "x")), assign = true))
    assertEquals(declaration, "def __init__(self, x: float):\n    self.x = x")
    val added = dialect.addConstructor(walker, base, declaration)
    assertEquals(analysis(added).classes(2).fields.map(field => (field.name, field.javaType)), List(("x", "float")))
    assert(dialect.duplicateConstructor(analysis(walker).classes(1), Nil))
  }

  test("deleting a member removes its lines, keeps one blank line and leaves pass in an emptied block") {
    val main = module(walker)
    val helper = main.methods.head
    val removed = dialect.removeMember(walker, helper.start, helper.end)
    assert(removed.contains("class Base:\n    pass\n\n\ndef main():"), removed)
    assertEquals(module(removed).methods.map(_.name), List("main"))
    val withMethod = dialect.addMethod(walker, analysis(walker).classes(2), "def area(self):\n    pass")
    val area = analysis(withMethod).classes(2).methods.head
    assert(dialect.removeMember(withMethod, area.start, area.end).contains("class Base:\n    pass\n"), withMethod)
  }

  test("converted Java turtle programs read as a module with their functions") {
    val java = "public class Zeichnung {\n  public static void main(String[] args) {\n    Turtle.forward(60);\n    quadrat(20);\n  }\n" +
      "  static void quadrat(double seite) {\n    Turtle.forward(seite);\n  }\n}\n"
    val python = ProgrammingStateRaw(Java, java).toPythonStructured.toString
    assert(!python.contains("-> None"), python)
    val current = analysis(python)
    assertEquals(current.classes.map(_.name), List(""), python)
    assertEquals(current.classes.head.methods.map(_.name).toSet, Set("main", "quadrat"), python)
    assertEquals(current.initialMethod(requirePublicMain = false).map(_.name), Some("main"))
    assertEquals(ProgrammingStatePythonStructured(python).toString, python)
  }

  test("comments indented into a function belong to it, so a new function follows them") {
    val source = "import turtle\n\ndef main():\n    turtle.forward(60)\n# old: turtle.left(45)\n    turtle.left(90)\n    # TODO\nmain()\n"
    val main = module(source).methods.head
    assertEquals(source.substring(main.start, main.end),
      "def main():\n    turtle.forward(60)\n# old: turtle.left(45)\n    turtle.left(90)\n    # TODO")
    val added = dialect.addMethod(source, module(source), "def quadrat(seite: float):\n    pass")
    assertEquals(added, "import turtle\n\ndef main():\n    turtle.forward(60)\n# old: turtle.left(45)\n    turtle.left(90)\n    # TODO\n\n" +
      "def quadrat(seite: float):\n    pass\nmain()\n")
    val commented = "class Box:\n    # the size\n    def size(self):\n        return 1\n"
    val size = analysis(commented).classes(1).methods.head
    assertEquals(dialect.removeMember(commented, size.start, size.end), "class Box:\n    # the size\n    pass\n")
  }

  test("names and types follow Python's rules") {
    assert(dialect.validName("draw_square") && !dialect.validName("class") && !dialect.validName("2d"))
    assert(dialect.validType("") && dialect.validType("list[int]") && dialect.validType("dict[str, Walker] | None"))
    assert(!dialect.validType("list[int") && !dialect.validType("def"))
    assertEquals(dialect.memberName("count", Visibility.Private), "__count")
    assertEquals(dialect.bareName("__count"), "count")
    assertEquals(dialect.memberName("__init__", Visibility.Private), "__init__")
  }
}
