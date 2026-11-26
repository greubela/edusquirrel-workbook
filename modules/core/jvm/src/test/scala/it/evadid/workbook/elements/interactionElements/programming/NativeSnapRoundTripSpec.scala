package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** A project built in the native Snap editor (`say-join-free-scripts.xml`): loops, a coordinate jump, `say`,
  * `say … for … secs` and `join` in a green-flag script, plus free scripts that never run. Through Python or
  * Java and back it keeps its program: `say` prints (`print`, `System.out.println`), `join` joins text, and the
  * free scripts become the uncalled `unused_code` function and a free script again.
  */
class NativeSnapRoundTripSpec extends FunSuite {
  private val native = ProgrammingStateRaw(SnapLanguage, NativeSnapFixtures.read("say-join-free-scripts.xml"))

  private def converted(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState = {
    val result = state.convertToLanguage(language)
    assert(!result.hasLostFunctionality, s"$language lost functionality: ${result.conversionInfo}\n$state")
    result.finalState.getOrElse(fail(s"no $language state for\n$state\n${result.conversionInfo}"))
  }
  private def commands(state: ProgrammingState) =
    state.toBeExpression.turtleCommandExecutionInfo.completedCommands.map(command => command.getClass.getSimpleName -> command.args)
  private def text(state: ProgrammingState, language: ProgrammingLanguage): String = language match {
    case Python => state.toPythonStructured.toString
    case Java => state.toJavaStructured.toString
    case _ => state.toSnapXml.code
  }
  private def block(code: String, start: String): String = {
    val from = code.indexOf(start)
    assert(from >= 0, s"$start in\n$code")
    code.substring(from).linesIterator.drop(1).takeWhile(line => line.startsWith(" ") || line.isEmpty).mkString("\n")
  }

  test("the native project converts to Python with prints, joined text and the free scripts as unused code") {
    val python = text(converted(native, Python), Python)
    val main = block(python, "def main():")
    assert(main.contains("print(\"Hallo \" + str(3 + 5))"), python)
    assert(main.contains("turtle.goto(0, 0)") && main.contains("while not (3 < 5):") && main.contains("for i in range(1, 11):"), python)
    val unused = block(python, "def unused_code():")
    assertEquals(unused.linesIterator.map(_.trim).toList, List("print(\"Hallo!\")", "turtle.setheading(90)"))
    assert(!main.contains("unused_code"), "free scripts never run")
  }

  test("the native project converts to Java with System.out.println") {
    val java = text(converted(native, Java), Java)
    assert(java.contains("System.out.println(\"Hallo \" + String.valueOf(3 + 5));"), java)
    assert(block(java, "static void unusedCode()").contains("System.out.println(\"Hallo!\");"), java)
  }

  for (language <- Seq(Python, Java)) test(s"Snap -> $language -> Snap -> $language keeps the program") {
    val first = converted(native, language)
    val snap = converted(first, SnapLanguage)
    val xml = snap.toSnapXml.code
    assert(xml.contains("<block s=\"reportJoinWords\"><list><l>Hallo </l>"), s"text is joined in Snap:\n$xml")
    assert(xml.contains("<block s=\"bubble\"><l>Hallo!</l></block>"), xml)
    val scripts = "<script x=\"[^\"]*\" y=\"[^\"]*\">(<block s=\"[^\"]*\")".r.findAllMatchIn(xml).map(_.group(1)).toList
    assertEquals(scripts.count(_ == "<block s=\"receiveGo\""), 1, s"one green-flag script and a free one:\n$xml")
    assert(scripts.size == 2, xml)
    val again = converted(snap, language)
    assertEquals(text(again, language), text(converted(converted(again, SnapLanguage), language), language), "a second route changes nothing")
    for (state <- Seq(first, snap, again)) assertEquals(commands(state), commands(native), s"draws as the native project:\n$state")
    assert(commands(native).nonEmpty)
  }
}
