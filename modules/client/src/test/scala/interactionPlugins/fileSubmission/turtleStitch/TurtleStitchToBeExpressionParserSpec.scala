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
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExercise, ProgrammingExerciseState, SnapTurtlePythonBridge}
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

  test("recovery mixed literal and nested-block inputs keep full arity") {
    // Incomplete project XML forces the string recovery path (model parse has no statements).
    val malformedXml =
      """<project><scenes><scene><stage><sprites><sprite><scripts><script><block s="gotoXY"><l>10</l><block var="x"/></block><variables><variable name="x"></variable>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(malformedXml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("goto_x_y(10, x)"), clue = python)
  }

  test("gotoXY with mixed literal and variable reporter keeps both arguments") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="gotoXY"><l>10</l><block var="x"/></block></script></scripts></sprite></sprites></stage><variables><variable name="x"></variable></variables></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("goto_x_y(10, x)"), clue = python)
    assert(!python.contains("goto_x_y(10)"), clue = python)
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

  test("ProgrammingExercise xml persist roundtrips scripts across reload") {
    val state = ProgrammingExerciseState(twoScriptsXml)
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    assert(stored.startsWith("SNAP_XML_V1"), clue = stored.take(80))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(restored.snapXml.contains("""<script x="70" y="80">"""), clue = restored.snapXml)
    assert(restored.snapXml.contains("""<script x="200" y="150">"""), clue = restored.snapXml)
    assertEquals("""<script x="[^"]+" y="[^"]+">""".r.findAllIn(restored.snapXml).size, 2)
  }

  test("doRepeat XML parses to BeRepeatNr and roundtrips") {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xmlWithRepeatNoPentrails)
    val body = parsed.expression.asInstanceOf[BeStartProgram].startSequence.toList.flatMap(_.body)
    assert(body.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeRepeatNr]), clue = body)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "repeat", parsed.canvasLayout)
    assert(xmlOut.contains("""s="doRepeat""""), clue = xmlOut)
    assert(xmlOut.contains("""<l>10</l>"""), clue = xmlOut)
    assert(xmlOut.contains("""s="forward""""), clue = xmlOut)
  }

  test("doIfElse XML roundtrips through serializer") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIfElse"><block s="reportTrue"></block><script><block s="forward"><l>12</l></block></script><script><block s="turn"><l>30</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val body = parsed.expression.asInstanceOf[BeStartProgram].startSequence.toList.flatMap(_.body)
    assert(body.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeIfElse]), clue = body)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "ifelse", parsed.canvasLayout)
    assert(xmlOut.contains("""s="doIfElse""""), clue = xmlOut)
    assert(xmlOut.contains("""s="reportTrue""""), clue = xmlOut)
  }

  test("doIf with variable reporter in equals prints python if test == 1") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicEquals"><list><block var="test"/><l>1</l></list></block><script><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>18</l></block></script></block></script><list></list></block></script></scripts></sprite></sprites></stage><variables><variable name="test"></variable></variables></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    assert(
      TurtleStitchToBeExpressionParser.hasSupportedStatements(parsed.expression),
      clue = parsed.expression
    )
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("if test == 1:"), clue = python)
    assert(python.contains("for _ in range(10):"), clue = python)
    assert(python.contains("forward(10)"), clue = python)
    assert(python.contains("turn(18)"), clue = python)
    assert(!python.contains("do_if"), clue = python)
    assert(!python.contains("report_variadic_equals"), clue = python)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "equals-var", parsed.canvasLayout)
    assert(xmlOut.contains("""s="reportVariadicEquals""""), clue = xmlOut)
    assert(xmlOut.contains("""<block var="test"/>"""), clue = xmlOut)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""s="reportVariadicEquals""""), clue = reXml)
    assert(reXml.contains("""<block var="test"/>"""), clue = reXml)
  }

  test("reportVariadicLessThan in doIf prints infix comparison python") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicLessThan"><list><l>1</l><l>2</l></list></block><script><block s="forward"><l>10</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("if 1 < 2:"), clue = python)
    assert(!python.contains("<("), clue = python)
  }

  test("printed repeat python reapplies without comment rejection") {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xmlWithRepeatNoPentrails)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("for _ in range(10):"), clue = python)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
  }

  test("doSetVar XML roundtrips to python assignment and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>steps</l><l>10</l></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("steps = 10"), clue = python)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "vars", parsed.canvasLayout)
    assert(xmlOut.contains("""s="doSetVar""""), clue = xmlOut)
    assert(xmlOut.contains("""<l>steps</l>"""), clue = xmlOut)
    assert(xmlOut.contains("""<variable name="steps">"""), clue = xmlOut)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
  }

  test("doChangeVar and nested variable reporter roundtrip") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>steps</l><l>1</l></block><block s="doChangeVar"><l>steps</l><l>2</l></block><block s="doIf"><block s="reportVariadicLessThan"><list><block var="steps"/><l>10</l></list></block><script><block s="forward"><block var="steps"/></block></script></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("steps = 1"), clue = python)
    assert(python.contains("steps = steps + 2"), clue = python)
    assert(python.contains("if steps < 10:"), clue = python)
    assert(python.contains("forward(steps)"), clue = python)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "vars", parsed.canvasLayout)
    assert(xmlOut.contains("""s="doChangeVar""""), clue = xmlOut)
    assert(xmlOut.contains("""<block var="steps"/>"""), clue = xmlOut)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""s="doChangeVar""""), clue = reXml)
    assert(reXml.contains("""<variable name="steps">"""), clue = reXml)
  }

  test("mixed palette blocks roundtrip Snap XML to python and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="down"></block><block s="doSetVar"><l>steps</l><l>10</l></block><block s="doRepeat"><l>4</l><script><block s="forward"><l>50</l></block><block s="turn"><l>90</l></block></script></block><block s="doIfElse"><block s="reportVariadicLessThan"><list><block var="steps"/><l>20</l></list></block><script><block s="gotoXY"><l>10</l><l>20</l></block></script><script><block s="setHeading"><l>90</l></block></script></block><block s="doUntil"><block s="reportFalse"></block><script><block s="up"></block></script></block><block s="clear"></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    assert(
      TurtleStitchToBeExpressionParser.hasSupportedStatements(parsed.expression),
      clue = parsed.expression
    )

    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("steps = 10"), clue = python)
    assert(python.contains("for _ in range(4):"), clue = python)
    assert(python.contains("forward(50)"), clue = python)
    assert(python.contains("turn(90)"), clue = python)
    assert(python.contains("if steps < 20:"), clue = python)
    assert(python.contains("goto_x_y(10, 20)"), clue = python)
    assert(python.contains("set_heading(90)"), clue = python)
    assert(python.contains("while True:"), clue = python)
    assert(python.contains("down()"), clue = python)
    assert(python.contains("up()"), clue = python)
    assert(python.contains("clear()"), clue = python)

    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)

    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""s="doRepeat""""), clue = reXml)
    assert(reXml.contains("""s="doIfElse""""), clue = reXml)
    assert(reXml.contains("""s="doUntil""""), clue = reXml)
    assert(reXml.contains("""s="gotoXY""""), clue = reXml)
    assert(reXml.contains("""s="setHeading""""), clue = reXml)
    assert(reXml.contains("""s="doSetVar""""), clue = reXml)
    assert(reXml.contains("""s="clear""""), clue = reXml)
  }

  test("doWait block roundtrips Snap XML to python and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="doWait"><l>1</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val stored = ProgrammingExercise.StateSerializer.serialize(ProgrammingExerciseState(xml))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(restored.snapXml.contains("""s="doWait""""), clue = restored.snapXml)

    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    assert(
      TurtleStitchToBeExpressionParser.hasSupportedStatements(parsed.expression),
      clue = parsed.expression
    )
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("do_wait(1)"), clue = python)

    val derived = it.evadid.homepage.webElements.editor.code.SnapEditor.SnapProgramDerivation.fromXml(xml)
    assert(derived.pythonCompatible, clue = derived)
    assert(!derived.unsupportedSelectors.contains("doWait"), clue = derived.unsupportedSelectors)

    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    assert(applied.toOption.get.snapXml.contains("""s="doWait""""), clue = applied.toOption.get.snapXml)
  }

  test("doFor XML roundtrips to python for-range and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doFor"><l>i</l><l>1</l><l>4</l><script><block s="forward"><block s="reportVariadicProduct"><list><block var="i"/><l>10</l></list></block></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    assert(
      TurtleStitchToBeExpressionParser.hasSupportedStatements(parsed.expression),
      clue = parsed.expression
    )
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("for i in range(1, 4 + 1):"), clue = python)
    assert(python.contains("forward(i * 10)"), clue = python)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "dofor", parsed.canvasLayout)
    assert(xmlOut.contains("""s="doFor""""), clue = xmlOut)
    assert(xmlOut.contains("""s="reportVariadicProduct""""), clue = xmlOut)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    assert(applied.toOption.get.snapXml.contains("""s="doFor""""), clue = applied.toOption.get.snapXml)
  }

  test("custom block-definition XML roundtrips with body and call") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="other"><inputs><input type="%n" name="n">n</input></inputs><scripts><script><block s="forward"><block var="n"/></block><block s="turn"><l>90</l></block></script></scripts></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("def square"), clue = python)
    assert(python.contains("forward(n)"), clue = python)
    assert(python.contains("square(50)"), clue = python)
    val xmlOut = TurtleStitchFromBeExpressionSerializer.toXml(parsed.expression, "square", parsed.canvasLayout)
    assert(xmlOut.contains("""<block-definition s="square %n""""), clue = xmlOut)
    assert(xmlOut.contains("""<custom-block s="square %n">"""), clue = xmlOut)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""<block-definition s="square %n""""), clue = reXml)
    assert(reXml.contains("""<custom-block s="square %n">"""), clue = reXml)
  }

  test("native Snap block-definition script child prints the function body") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="Variables"><inputs><input type="%n" name="n">n</input></inputs><script><block s="forward"><block var="n"/></block><block s="turn"><l>90</l></block></script></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("def square(n):"), clue = python)
    assert(python.contains("forward(n)"), clue = python)
    assert(python.contains("turn(90)"), clue = python)
    assert(python.contains("square(50)"), clue = python)
  }

  test("native Snap semantic spec plus type-spec call prints one function") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %size" type="command" category="Variables"><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("def square(size):"), clue = python)
    assert(python.contains("forward(size)"), clue = python)
    assert(python.contains("square(50)"), clue = python)
    assertEquals(python.split("def square").length - 1, 1, clue = python)
    assert(SnapTurtlePythonBridge.isPythonCompatibleXml(xml), clue = xml)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""<block-definition s="square %size""""), clue = reXml)
    assert(reXml.contains("""<custom-block s="square %n">"""), clue = reXml)
  }

  test("setColor setXPosition setYPosition and setSize roundtrip to canonical python names") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="setColor"><color>255,0,0,1</color></block><block s="setXPosition"><l>10</l></block><block s="setYPosition"><l>20</l></block><block s="setSize"><l>3</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("color("), clue = python)
    assert(!python.contains("set_color"), clue = python)
    assert(python.contains("set_x(10)"), clue = python)
    assert(!python.contains("set_x_position"), clue = python)
    assert(python.contains("set_y(20)"), clue = python)
    assert(!python.contains("set_y_position"), clue = python)
    assert(python.contains("pensize(3)"), clue = python)
    assert(!python.contains("set_size"), clue = python)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""s="setColor""""), clue = reXml)
    assert(reXml.contains("<color>"), clue = reXml)
    assert(reXml.contains("""s="setXPosition""""), clue = reXml)
    assert(reXml.contains("""s="setYPosition""""), clue = reXml)
    assert(reXml.contains("""s="setSize""""), clue = reXml)
  }

  test("python aliases roundtrip through Snap XML") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="turn"><l>90</l></block><block s="turnLeft"><l>15</l></block><block s="gotoXY"><l>1</l><l>2</l></block><block s="up"></block><block s="down"></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("turn(90)"), clue = python)
    assert(python.contains("turn_left(15)"), clue = python)
    assert(python.contains("goto_x_y(1, 2)"), clue = python)
    val applied = SnapTurtlePythonBridge.applyPython(
      """right(90)
        |left(15)
        |goto(1, 2)
        |penup()
        |pendown()
        |""".stripMargin
    )
    assert(applied.isRight, clue = applied)
    val reXml = applied.toOption.get.snapXml
    assert(reXml.contains("""s="turn""""), clue = reXml)
    assert(reXml.contains("""s="turnLeft""""), clue = reXml)
    assert(reXml.contains("""s="gotoXY""""), clue = reXml)
    assert(reXml.contains("""s="up""""), clue = reXml)
    assert(reXml.contains("""s="down""""), clue = reXml)
  }

  test("native color slot roundtrips without changing arity") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="setColor"><color>0,0,255,1</color></block><block s="forward"><l>10</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("color("), clue = python)
    assert(!python.contains("color(0, 0, 255"), clue = python)
    val applied = SnapTurtlePythonBridge.applyPython(python)
    assert(applied.isRight, clue = applied)
    assert(applied.toOption.get.snapXml.contains("<color>0,0,255,1</color>") || applied.toOption.get.snapXml.contains("<color>"), clue = applied.toOption.get.snapXml)
  }

  test("custom block arity mismatch is rejected on python apply") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="other"><inputs><input type="%n" name="n">n</input></inputs><scripts><script><block s="forward"><block var="n"/></block></script></scripts></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    assert(python.contains("def square"), clue = python)
    val mismatched = python.stripSuffix("\n") + "\nsquare(1, 2)\n"
    val applied = SnapTurtlePythonBridge.applyPython(mismatched)
    assert(applied.isLeft, clue = applied)
  }
}


