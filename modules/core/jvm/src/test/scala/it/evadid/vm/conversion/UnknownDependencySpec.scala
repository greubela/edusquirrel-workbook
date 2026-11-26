package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDefineFunction, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.UnknownDependencies
import it.evadid.vm.conversion.dependencies.UnknownDependencies.UnknownCall
import it.evadid.vm.types.BeDataType
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

import java.nio.file.Files
import javax.tools.ToolProvider

class UnknownDependencySpec extends FunSuite {
  private val program =
    """class Main {
      |  public static void main(String[] args) {
      |    System.out2.println("x");
      |    System.out2.println("x", 2);
      |    int size = Helper.size(3);
      |    String text = Config.screen.title.read();
      |    Object limit = Config.screen.limit;
      |    Config.screen.limit = size + 1;
      |    log(size, text);
      |    System.out.println(size);
      |  }
      |}""".stripMargin

  private def dependency(source: String, language: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage = Java) =
    ProgrammingStateRaw(language, source).toBeExpression.unknownDependency

  test("unresolved calls are collected with their written paths; known and user definitions are not") {
    val calls = UnknownDependencies.calls(ProgrammingStateRaw(Java, program).toBeExpression.expression)
    assertEquals(calls.toSet, Set(UnknownCall(Seq("System", "out2", "println"), 1), UnknownCall(Seq("System", "out2", "println"), 2),
      UnknownCall(Seq("Helper", "size"), 1), UnknownCall(Seq("Config", "screen", "title", "read"), 0), UnknownCall(Seq("log"), 2)))
    assertEquals(dependency("class Main { static void main(String[] args) { forward(10); System.out.println(1); draw(); }\n" +
      "  static void draw() {} }"), None, "turtle, system output and the program's own method are defined")
    assertEquals(dependency("def draw():\n    forward(10)\n\ndraw()\nprint(1)", Python), None)
  }

  test("unknown attribute reads become attributes of the same classes; bound names are not unknown") {
    val expression = ProgrammingStateRaw(Java, program).toBeExpression.expression
    assertEquals(UnknownDependencies.attributes(expression), Seq(UnknownDependencies.UnknownAttribute(Seq("Config", "screen", "limit"))))
    val screen = dependency(program).get.memberClasses.find(_.name.spelling() == "ConfigScreen").get
    assertEquals(screen.attributes.map(attribute => attribute.name.spelling() -> attribute.variableType),
      List("title" -> BeDataType.ClassType(it.evadid.vm.naming.BeEntityName.fromLiteral("ConfigScreenTitle")), "limit" -> BeDataType.AnyType))
    assertEquals(dependency("class Main { int size = 3; void f(Main other) { Object x = other.missing; } }"), None,
      "a member of a known parameter is a type error, not a missing dependency")
  }

  test("assignments to unknown attributes keep their source and make the attribute unknown") {
    val java = ProgrammingStateRaw(Java, "class Main { static void main(String[] args) { int a = 2; Settings.limit = a * 3; } }").toBeExpression
    assertEquals(UnknownDependencies.attributes(java.expression), Seq(UnknownDependencies.UnknownAttribute(Seq("Settings", "limit"))))
    assert(ProgrammingStateRaw(Java, "class Main { static void main(String[] args) { int a = 2; Settings.limit = a * 3; } }")
      .toBeExpression.toJava.code.contains("Settings.limit = a * 3;"))
    val python = ProgrammingStateRaw(Python, "a = 2\nSettings.limit = a * 3\n").toBeExpression
    assertEquals(python.unknownDependency.map(_.rootClasses.flatMap(_.attributes.map(_.name.spelling()))), Some(List("limit")))
    assert(python.toPython.code.contains("Settings.limit = a * 3"), python.toPython.code)
    // A comparison is not an assignment, and a bound receiver is the program's own object.
    assertEquals(UnknownDependencies.attributes(ProgrammingStateRaw(Python, "class P:\n    pass\n\np = P()\np.size = 2\n").toBeExpression.expression), Nil)
  }

  test("qualified paths become a static root class, attribute classes and their methods") {
    val unknown = dependency(program).getOrElse(fail("unknown calls expected"))
    def names(classes: List[it.evadid.vm.code.defining.BeDefineClass]) = classes.map(_.name.spelling())
    assertEquals(names(unknown.rootClasses), List("System", "Helper", "Config"))
    assertEquals(names(unknown.memberClasses), List("SystemOut2", "ConfigScreen", "ConfigScreenTitle"))
    val system = unknown.rootClasses.head
    assertEquals(system.attributes.map(attribute => attribute.name.spelling() -> attribute.variableType),
      List("out2" -> BeDataType.ClassType(it.evadid.vm.naming.BeEntityName.fromLiteral("SystemOut2"))))
    val println = unknown.memberClasses.head.methods
    assertEquals(println.map(method => method.functionTypeInfo.displayName.spelling() -> method.inputs.size), List("println" -> 1, "println" -> 2))
    assert(println.forall(_.functionTypeInfo.funcType == BeDefineFunction.Method))
    assertEquals(unknown.rootClasses(1).methods.map(_.functionTypeInfo.funcType), List(BeDefineFunction.StaticMethod))
    assertEquals(unknown.definedFunctions.map(_.functionTypeInfo.displayName.spelling()), List("log"))
    val serialized: BeExpression = unknown
    assertEquals(upickle.default.read[BeExpression](upickle.default.write(serialized)), serialized)
  }

  test("the generated Java stubs make the program compile") {
    val unknown = dependency(program).get
    val members = UnknownDependencies.javaFunctions(unknown).mkString("\n")
    // The stub class System shadows java.lang.System; it keeps `System.out`, which the last statement prints to.
    val source = program.stripSuffix("}") + members + "\n}\n\n" + UnknownDependencies.javaClasses(unknown)
    val directory = Files.createTempDirectory("unknown-dependency")
    val file = directory.resolve("Main.java")
    Files.writeString(file, source)
    val errors = java.io.ByteArrayOutputStream()
    val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(fail("a JDK compiler is required"))
    val status = compiler.run(null, null, errors, "-d", directory.toString, file.toString)
    assertEquals(status, 0, s"${errors.toString}\n$source")
  }

  test("the Python stubs define inner classes before the classes that instantiate them") {
    val python = UnknownDependencies.pythonDefinitions(dependency(program).get)
    assert(python.indexOf("class ConfigScreenTitle:") < python.indexOf("class ConfigScreen:"), python)
    assert(python.indexOf("class ConfigScreen:") < python.indexOf("class Config:"), python)
    assert(python.contains("class System:\n    out2 = SystemOut2()"), python)
    assert(python.contains("    @staticmethod\n    def size(*arguments):\n        return None"), python)
    assert(python.contains("def log(*arguments):\n    return None"), python)
  }
}
