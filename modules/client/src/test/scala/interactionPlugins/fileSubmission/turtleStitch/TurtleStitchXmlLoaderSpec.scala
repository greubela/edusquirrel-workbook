package interactionPlugins.fileSubmission.turtleStitch

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.{
  TurtleStitchToBeExpressionParser,
  TurtleStitchXmlLoader
}
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState
import munit.FunSuite
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*

class TurtleStitchXmlLoaderSpec extends FunSuite {

  private val comparisonWithVariableXml =
    """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicLessThan"><list><block var="steps"/><l>10</l></list></block><script><block s="forward"><l>1</l></block></script></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""

  test("load keeps list inputs on variadic operator blocks") {
    val project = TurtleStitchXmlLoader.load(comparisonWithVariableXml)
    val sprite = project.scenes.head.stage.sprites.head
    val doIf = sprite.scripts.head.blocks(1).asInstanceOf[PrimitiveBlock]
    val lessThan = doIf.inputs.collectFirst { case NestedBlock(value) => value }.get.asInstanceOf[PrimitiveBlock]

    assertEquals(lessThan.selector, Some("reportVariadicLessThan"))
    assertEquals(lessThan.inputs.length, 1)

    val listInput = lessThan.inputs.head.asInstanceOf[ListLiteral]
    assertEquals(listInput.items.length, 2)
    assertEquals(listInput.items.head.asInstanceOf[NestedBlock].value.asInstanceOf[PrimitiveBlock].variable, Some("steps"))
    assertEquals(listInput.items(1).asInstanceOf[Literal].value, "10")
  }

  test("model path renders variable equals as infix python") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicEquals"><list><block var="test"/><l>1</l></list></block><script><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>18</l></block></script></block></script><list></list></block></script></scripts></sprite></sprites></stage><variables><variable name="test"></variable></variables></scene></scenes></project>"""
    val project = TurtleStitchXmlLoader.load(xml)
    val sprite = project.scenes.head.stage.sprites.head
    val doIf = sprite.scripts.head.blocks(1).asInstanceOf[PrimitiveBlock]
    val equals = doIf.inputs.collectFirst { case NestedBlock(value) => value }.get.asInstanceOf[PrimitiveBlock]
    assertEquals(equals.selector, Some("reportVariadicEquals"))
    val listInput = equals.inputs.head.asInstanceOf[ListLiteral]
    assertEquals(listInput.items.head.asInstanceOf[NestedBlock].value.asInstanceOf[PrimitiveBlock].variable, Some("test"))

    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = ProgrammingExerciseState.pythonOf(ProgrammingExerciseState(BeProgram(parsed.expression), parsed.canvasLayout))
    assert(python.contains("if test == 1:"), clue = python)
  }

  test("model path renders variable comparisons as infix python") {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(comparisonWithVariableXml)
    val python = ProgrammingExerciseState.pythonOf(ProgrammingExerciseState(BeProgram(parsed.expression), parsed.canvasLayout))
    assert(python.contains("if steps < 10:"), clue = python)
    assert(!python.contains("<("), clue = python)
  }

  test("load parses simple_forward project XML without crashing".ignore) {
    val xml =
      """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>"""

    val project = TurtleStitchXmlLoader.load(xml)

    assertEquals(project.name, "simple_forward")
    assertEquals(project.scenes.length, 1)
    assertEquals(project.scenes.head.name, "simple_forward")
    assertEquals(project.scenes.head.stage.name, "Bühne")

    val sprite = project.scenes.head.stage.sprites.head
    assertEquals(sprite.name, "Objekt")
    assertEquals(sprite.scripts.length, 1)
    assertEquals(sprite.scripts.head.blocks.length, 2)
  }
}
