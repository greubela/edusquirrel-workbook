package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.{BlockDefinition, Block}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.BlockType
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.{Forward, Left}
import munit.FunSuite

/** Functions that return a value convert between Python, Java and Snap, where they are reporter and predicate blocks. */
class ReturningFunctionConversionSpec extends FunSuite {
  private val reporter = "def double(x: float) -> float:\n    return x * 2\n\nforward(double(5))\n"
  private val programs: List[(String, ProgrammingStateRaw)] = List(
    "a reporter used as an input" -> ProgrammingStateRaw(Python, reporter),
    "a predicate used as a condition" -> ProgrammingStateRaw(Python, "def big(n: float) -> bool:\n    return n > 10\n\nif big(20):\n    forward(5)\nelse:\n    back(5)\n"),
    "several returns" -> ProgrammingStateRaw(Python, "def size(n: float) -> float:\n    if n > 3:\n        return 30\n    return 10 * n\n\nfor i in range(5):\n    forward(size(i))\n    left(72)\n"),
    "a reporter calling a reporter and recursion" -> ProgrammingStateRaw(Python,
      "def double(x: float) -> float:\n    return x * 2\n\ndef fact(n: float) -> float:\n    if n <= 1:\n        return 1\n    return n * fact(n - 1)\n\nforward(double(fact(3)))\n"),
    "a Java method returning a value" -> ProgrammingStateRaw(Java,
      "public class Main {\n  static double half(double v) {\n    return v / 2;\n  }\n  public static void main(String[] args) {\n    Turtle.forward(half(40));\n  }\n}\n"))

  /** What each program draws, computed by hand. */
  private val drawings: Map[String, List[TurtleCommand[Double]]] = Map(
    "a reporter used as an input" -> List(Forward(10.0)),
    "a predicate used as a condition" -> List(Forward(5.0)),
    "several returns" -> List(0, 10, 20, 30, 30).flatMap(distance => List(Forward(distance.toDouble), Left(72.0))),
    "a reporter calling a reporter and recursion" -> List(Forward(12.0)),
    "a Java method returning a value" -> List(Forward(20.0)))

  private def turtle(state: ProgrammingState) = state.toBeExpression.turtleCommandExecutionInfo.completedCommands
  private def convert(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState =
    state.convertToLanguage(language).finalState.getOrElse(fail(s"No $language for:\n$state"))

  programs.foreach { (label, program) =>
    test(s"$label draws the same in every language and after converting back") {
      val expected = turtle(program)
      assertEquals(expected, drawings(label), "the source program itself draws the expected path")
      List(Python, Java, SnapLanguage).filterNot(_ == program.language).foreach { language =>
        val converted = convert(program, language)
        assertEquals(turtle(converted), expected, s"$label in $language")
        assertEquals(turtle(convert(converted, program.language)), expected, s"$label back from $language")
      }
    }
  }

  test("Snap writes reporters and predicates with report blocks, and native calls resolve") {
    val xml = ProgrammingStateRaw(Python, programs(3)._2.code + "\ndef big(n: float) -> bool:\n    return n > 10\n\nif big(2):\n    forward(1)\n").toSnapXml.code
    val model = ProgrammingStateRaw(SnapLanguage, xml).toSnapScalaModel.fold(fail(_), _.model)
    val definitions = (model.root +: model.root.descendants).collect { case d: BlockDefinition => d.spec.get -> d.blockType }
    assertEquals(definitions.toMap, Map("double %x" -> Some(BlockType.Reporter), "fact %n" -> Some(BlockType.Reporter),
      "big %n" -> Some(BlockType.Predicate)))
    assert((model.root +: model.root.descendants).collect { case b: Block => b.selector }.contains(Some("doReport")))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
    assert(SnapCustomBlockRules.globalDefinitions(xml).forall(_.isPythonCompatible), "reporters and predicates are written back")
  }

  test("signatures survive Snap: result types are inferred from what a reporter reports") {
    val python = (program: ProgrammingStateRaw) => convert(convert(program, SnapLanguage), Python).toString
    assert(python(programs.head._2).contains("def double(x: float) -> float:"), python(programs.head._2))
    assert(python(programs(1)._2).contains("def big(n: float) -> bool:"), python(programs(1)._2))
    assert(python(programs(2)._2).contains("def size(n: float) -> float:"), python(programs(2)._2))
    val java = convert(convert(programs(4)._2, SnapLanguage), Java).toString
    assert(java.contains("static double half(double v)"), java)
  }

  test("a return without a value has no Snap representation and is reported") {
    val program = ProgrammingStateRaw(Python, "def stop_early(n: float) -> None:\n    if n > 3:\n        return\n    forward(n)\n\nstop_early(2)\n")
    val result = program.convertToLanguage(SnapLanguage)
    assertEquals(result.finalState, None)
    assert(result.conversionInfo.exists(_.warningMsg.contains("return without a value")), result.conversionInfo.toString)
  }
}
