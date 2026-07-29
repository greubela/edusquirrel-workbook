package interactionPlugins.fileSubmission.turtleStitch

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.{
  TurtleStitchFromBeExpressionSerializer,
  TurtleStitchToBeExpressionParser
}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExercise, ProgrammingExerciseState}
import munit.FunSuite

class TurtleStitchToBeExpressionParserSpec extends FunSuite {

  private val xmlWithRepeatNoPentrails =
    """<project name="test2" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="test2"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="156" y="66"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>15</l></block></script></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""

  private val simpleForwardXml =
    """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>"""

  test("parser builds two-phase output with definitions first and calls afterwards".ignore) {
    val expression = TurtleFileSubmission.parseToBeExpression(xmlWithRepeatNoPentrails)
    val start = expression.asInstanceOf[BeStartProgram]
    val body = start.startSequence.getOrElse(BeSequence.optionalBody(Nil)).body

    val firstCallIndex = body.indexWhere(_.isInstanceOf[BeFunctionCall])
    assert(firstCallIndex > 0)
    assert(body.take(firstCallIndex).forall(_.isInstanceOf[BeDefineFunction]))
    assert(body.drop(firstCallIndex).exists(_.isInstanceOf[BeFunctionCall]))
  }

  test("malformed XML is handled safely in BeExpression parser") {
    val malformedXml = "<project><scenes><scene><stage><sprites><sprite><scripts><script><block s=\"forward\"><l>10</l>"
    val expression = TurtleFileSubmission.parseToBeExpression(malformedXml)
    assert(expression.isInstanceOf[BeStartProgram])
  }

  test("simple_forward XML converts to BeExpression without error") {
    val parseAttempt = scala.util.Try(TurtleFileSubmission.parseToBeExpression(simpleForwardXml))

    assert(parseAttempt.isSuccess)
    assert(parseAttempt.get.isInstanceOf[BeStartProgram])
  }

  test("simple_forward XML yields callable function calls") {
    val expression = TurtleFileSubmission.parseToBeExpression(simpleForwardXml)
    val body = expression.asInstanceOf[BeStartProgram].startSequence.toList.flatMap(_.body)
    assert(body.exists(_.isInstanceOf[BeFunctionCall]), clue = s"expected calls, got: $body")
    assertEquals(body.count(_.isInstanceOf[BeFunctionCall]), 2)
  }

  private val twoScriptsXml =
    """<project name="two" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="two"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script><script x="200" y="150"><block s="turn"><l>90</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  test("two top-level scripts produce layout sidecar and stay separate on XML roundtrip") {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(twoScriptsXml)
    assertEquals(parsed.canvasLayout.scripts.size, 2)
    assertEquals(parsed.canvasLayout.scripts.map(_.callCount), List(2, 1))
    assertEquals(parsed.canvasLayout.scripts(1).x, 200)
    assertEquals(parsed.canvasLayout.scripts(1).y, 150)

    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "two", parsed.canvasLayout)
    val scriptTags = """<script x="[^"]+" y="[^"]+">""".r.findAllIn(xmlOut).toList
    assertEquals(scriptTags.size, 2, clue = xmlOut)
    assert(xmlOut.contains("""<script x="70" y="80">"""), clue = xmlOut)
    assert(xmlOut.contains("""<script x="200" y="150">"""), clue = xmlOut)
    // Orphan script must not gain a synthetic receiveGo.
    val orphanSection = xmlOut.substring(xmlOut.indexOf("""<script x="200" y="150">"""))
    assert(!orphanSection.contains("""s="receiveGo""""), clue = orphanSection)
    assert(orphanSection.contains("""s="turn""""), clue = orphanSection)
  }

  test("ProgrammingExercise composite persist roundtrips layout across reload") {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(twoScriptsXml)
    val state = ProgrammingExerciseState(BeProgram(parsed.expression), parsed.canvasLayout)
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    assert(stored.startsWith("SNAP_LAYOUT_V1"), clue = stored.take(80))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored.canvasLayout.scripts.size, 2)
    assertEquals(restored.canvasLayout.scripts.map(_.callCount), List(2, 1))

    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(
      restored.program.fullProgram,
      "reloaded",
      restored.canvasLayout
    )
    assertEquals("""<script x="[^"]+" y="[^"]+">""".r.findAllIn(xmlOut).size, 2)
  }
}
