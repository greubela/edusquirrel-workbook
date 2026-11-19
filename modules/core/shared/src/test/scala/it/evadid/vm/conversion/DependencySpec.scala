package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeIntrinsic}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

class DependencySpec extends FunSuite {
  private def nodes(state: ProgrammingState) = ModuleLayout.nodes(state.toBeExpression.expression)
  private def intrinsics(state: ProgrammingState) = nodes(state).collect { case call: BeFunctionCall => call.funcDef.intrinsic }.flatten.toSet
  private def userDefinitions(state: ProgrammingState) = nodes(state).collect {
    case definition: BeDefineClass => definition.name.universalInterpretation()
    case definition: BeDefineFunction if definition.functionTypeInfo.isNamed.nonEmpty && definition.intrinsic.isEmpty &&
        definition.functionTypeInfo.funcType == BeDefineFunction.Function() => definition.functionTypeInfo.displayName.universalInterpretation()
  }.toSet

  test("turtle programs carry the library each language needs and parse it back as the library, not a user class") {
    val python = ProgrammingStateRaw(Python, "forward(100)\nright(90)\nforward(50)")
    val java = python.toJava.code
    assert(java.contains("class Turtle {"), java)
    assert(java.contains("public static void forward(double distance)"), java)
    assertEquals(KnownDependencies.requiredBy(python.toBeExpression.expression), Seq(KnownDependencies.turtle))
    val fromJava = ProgrammingStateRaw(Java, java)
    assertEquals(userDefinitions(fromJava), Set.empty[String], "the library class is not a user definition")
    assertEquals(intrinsics(fromJava), Set(BeIntrinsic.Turtle))
    val backToPython = fromJava.toPython.code
    assert(backToPython.startsWith("import turtle\n"), backToPython)
    assert(!backToPython.contains("class Turtle"), backToPython)
    assertEquals(fromJava.toBeExpression.turtleCommandExecutionInfo.completedCommands, python.toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }

  test("turtle methods whose name is reserved in Java use a turtle alias that reads back") {
    val python = ProgrammingStateRaw(Python, "goto(10, 20)\nforward(5)")
    val java = python.toJava.code
    assert(java.contains("Turtle.setPosition(10, 20);"), java)
    assert(!java.contains("goto"), java)
    assert(ProgrammingStateRaw(Java, java).toPython.code.contains("turtle.goto(10, 20)"))
  }

  test("a changed library class is the user's own class and keeps the existing name conflict check") {
    val java = ProgrammingStateRaw(Python, "forward(100)").toJava.code.replace("move(distance);", "move(2 * distance);")
    intercept[Exception](ProgrammingStateRaw(Java, java).toBeExpression)
  }

  test("text behind a URL needs a helper in Python and Java and the url reporter in Snap") {
    val python = ProgrammingStateRaw(Python, "page = fetch_text(\"https://example.org\")\nprint(page)")
    assertEquals(intrinsics(python), Set(BeIntrinsic.FetchText, BeIntrinsic.Print))
    val printedPython = python.toBeExpression.toPython.code
    assert(printedPython.contains("def fetch_text(url: str) -> str:"), printedPython)
    assert(printedPython.contains("urllib.request.urlopen(url)"), printedPython)
    assertEquals(userDefinitions(ProgrammingStateRaw(Python, printedPython)), Set.empty[String], "the helper is library code")
    val java = python.toJava.code
    assert(java.contains("class Web {") && java.contains("Web.fetchText(\"https://example.org\")"), java)
    assert(java.contains("System.out.println(page);"), java)
    assertEquals(ProgrammingStateRaw(Java, java).toPython.code, printedPython)
    val snap = python.toSnapXml.code
    assert(snap.contains("""<block s="reportURL">"""), snap)
    assert(snap.contains("""<block s="bubble">"""), snap)
    assertEquals(ProgrammingStateRaw(SnapLanguage, snap).toPython.code.linesIterator.count(_.contains("fetch_text(")), 2)
  }

  test("an edited helper is an ordinary user function") {
    val edited = ProgrammingStateRaw(Python, "def fetch_text(url: str) -> str:\n    return url\nprint(fetch_text(\"x\"))")
    assertEquals(userDefinitions(edited), Set("fetch_text"))
    assertEquals(intrinsics(edited), Set(BeIntrinsic.Print))
  }
}
