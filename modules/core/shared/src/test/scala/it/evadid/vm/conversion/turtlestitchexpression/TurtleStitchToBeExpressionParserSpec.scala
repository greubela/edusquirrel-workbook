package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.core.datastructures.language.AppLanguage.{Python, SnapLanguage}
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.pythonsnap.PythonToSnap
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.toXml
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.vm.conversion.snappython.SnapProgramDerivation
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.code.others.BeStartProgram
import munit.FunSuite

class TurtleStitchToBeExpressionParserSpec extends FunSuite {
  import TurtleStitchToBeExpressionParser.ParseWithLayout

  private def parse(xml: String): ParseWithLayout =
    TurtleStitchToBeExpressionParser.parseProjectWithLayout(SourceParsing.parse(xml, SourceLanguage.Snap).project.toTry.get)

  private def printed(project: ParseWithLayout): String = ExpressionToPython.printedPython(project.expression)

  private def written(project: ParseWithLayout, name: String): String =
    SnapProjectXml.toXml(project.expression, name, project.canvasLayout)

  private def assertSupported(project: ParseWithLayout): Unit =
    assert(SnapExpressionSupport.hasSupportedStatements(project.expression), clue = project.expression)

  private def appliedXml(applied: Either[String, ProgrammingStateRaw]): String = {
    assert(applied.isRight, clue = applied)
    applied.toOption.get.code
  }

  private def firstArgs(state: ProgrammingState) =
    state.toBeExpression.turtleCommandExecutionInfo.completedCommands.map(_.args.head)

  private def nonEmptyLines(text: String): List[String] = text.linesIterator.map(_.trim).filter(_.nonEmpty).toList

  private def assertContainsAll(text: String, fragments: String*): Unit =
    fragments.foreach(fragment => assert(text.contains(fragment), clue = text))

  private def assertContainsNone(text: String, fragments: String*): Unit =
    fragments.foreach(fragment => assert(!text.contains(fragment), clue = text))

  private val xmlWithRepeatNoPentrails =
    """<project name="test2" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="test2"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="156" y="66"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>15</l></block></script></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""

  test("truncated XML is reported as unparsable rather than read as a partial or empty program") {
    Seq("<project><scenes><scene><stage><sprites><sprite><scripts><script><block s=\"forward\"><l>10</l>",
        """<project><scenes><scene><stage><sprites><sprite><scripts><script><block s="gotoXY"><l>10</l><block var="x"/></block><variables><variable name="x"></variable>""")
      .foreach { truncated =>
        val error = intercept[it.evadid.vm.code.errors.ProgrammingConversionException](parse(truncated).expression)
        assertEquals(error.error.kind, it.evadid.vm.code.errors.ProgrammingConversionKind.Unparsable)
      }
  }

  test("an empty number slot is 0 in its place, not a missing operand") {
    val xml = """<project><scenes><scene><stage><sprites><sprite><scripts><script><block s="receiveGo"/>""" +
      """<block s="forward"><block s="reportPower"><l>3</l><l></l></block></block>""" +
      """<block s="forward"><block s="reportDifference"><l></l><l>2</l></block></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val state = ProgrammingStateRaw(SnapLanguage, xml)
    val python = state.toPython.code
    assert(python.contains("3 ** 0") && python.contains("0 - 2"), python)
    val drawn = firstArgs(state)
    assertEquals(drawn, List(1.0, -2.0))
    assertEquals(firstArgs(ProgrammingStateRaw(Python, python)), drawn)
    // Primitive number slots too: native Snap moves 0 steps for `forward ()`.
    val empty = ProgrammingStateRaw(SnapLanguage, xml.replace(
      """<block s="forward"><block s="reportPower"><l>3</l><l></l></block></block>""", """<block s="forward"><l></l></block>"""))
    assertEquals(firstArgs(empty), List(0.0, -2.0))
    assert(empty.toPython.code.contains("forward(0)"), empty.toPython.code)
  }

  test("gotoXY with mixed literal and variable reporter keeps both arguments") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="gotoXY"><l>10</l><block var="x"/></block></script></scripts></sprite></sprites></stage><variables><variable name="x"></variable></variables></scene></scenes></project>"""
    val python = printed(parse(xml))
    assert(python.contains("turtle.goto(10, x)"), clue = python)
    assert(!python.contains("turtle.goto(10)"), clue = python)
  }

  private val twoScriptsXml =
    """<project name="two" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="two"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script><script x="200" y="150"><block s="turn"><l>90</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  test("two top-level scripts produce layout sidecar and stay separate on XML roundtrip") {
    val parsed = parse(twoScriptsXml)
    assertEquals(parsed.canvasLayout.scripts.size, 2)
    assertEquals(parsed.canvasLayout.scripts.map(_.callCount), List(1, 1))
    assertEquals(parsed.canvasLayout.scripts.map(_.hasReceiveGo), List(true, false))
    assertEquals(parsed.canvasLayout.scripts(1).x, 200)
    assertEquals(parsed.canvasLayout.scripts(1).y, 150)

    val xmlOut = written(parsed, "two")
    val scriptTags = """<script x="[^"]+" y="[^"]+">""".r.findAllIn(xmlOut).toList
    assertEquals(scriptTags.size, 2, clue = xmlOut)
    assertContainsAll(xmlOut, """<script x="70" y="80">""", """<script x="200" y="150">""")
    // Orphan script must not gain a synthetic receiveGo.
    val orphanSection = xmlOut.substring(xmlOut.indexOf("""<script x="200" y="150">"""))
    assert(!orphanSection.contains("""s="receiveGo""""), clue = orphanSection)
    assert(orphanSection.contains("""s="turn""""), clue = orphanSection)
  }

  test("doRepeat XML parses to BeRepeatNr and roundtrips") {
    val parsed = parse(xmlWithRepeatNoPentrails)
    val body = parsed.expression.asInstanceOf[BeStartProgram].startSequence.toList.flatMap(_.body)
    assert(body.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeRepeatNr]), clue = body)
    val xmlOut = written(parsed, "repeat")
    assertContainsAll(xmlOut, """s="doRepeat"""", """<l>10</l>""", """s="forward"""")
  }

  test("doIfElse XML roundtrips through serializer") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIfElse"><block s="reportTrue"></block><script><block s="forward"><l>12</l></block></script><script><block s="turn"><l>30</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = parse(xml)
    val body = parsed.expression.asInstanceOf[BeStartProgram].startSequence.toList.flatMap(_.body)
    assert(body.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeIfElse]), clue = body)
    val xmlOut = written(parsed, "ifelse")
    assertContainsAll(xmlOut, """s="doIfElse"""", """s="reportTrue"""")
  }

  test("doIf with variable reporter in equals prints python if test == 1") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicEquals"><list><block var="test"/><l>1</l></list></block><script><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>18</l></block></script></block></script><list></list></block></script></scripts></sprite></sprites></stage><variables><variable name="test"></variable></variables></scene></scenes></project>"""
    val parsed = parse(xml)
    assertSupported(parsed)
    val python = printed(parsed)
    assertContainsAll(python, "if test == 1:", "for _ in range(10):", "forward(10)", "turtle.right(18)")
    assertContainsNone(python, "do_if", "report_variadic_equals")
    val xmlOut = written(parsed, "equals-var")
    assertContainsAll(xmlOut, """s="reportVariadicEquals"""", """<block var="test"/>""")
    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml, """s="reportVariadicEquals"""", """<block var="test"/>""")
  }

  test("reportVariadicLessThan in doIf prints infix comparison python") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicLessThan"><list><l>1</l><l>2</l></list></block><script><block s="forward"><l>10</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assert(python.contains("if 1 < 2:"), clue = python)
    assert(!python.contains("<("), clue = python)
  }

  test("valued test = 0 roundtrip does not append a bare test line") {
    val program = SourceParsing.parse("test = 0\n", SourceLanguage.Python).parsed().program
    val xml = SnapProjectXml.toXml(program.fullProgram)
    val derived = SnapProgramDerivation.fromXml(xml)
    val lines = nonEmptyLines(derived.python)
    assert(!lines.contains("test"), clue = derived.python + "\n" + xml)
    assert(derived.pythonCompatible, clue = (derived.unsupportedSelectors, derived.python))

    val nested =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><block var="test"/><l>0</l></block></script></scripts></sprite></sprites></stage><variables><variable name="test"><l>0</l></variable></variables></scene></scenes></project>"""
    val nestedDerived = SnapProgramDerivation.fromXml(nested)
    val nestedLines = nonEmptyLines(nestedDerived.python)
    assert(nestedLines.contains("test: Any = 0"), clue = nestedDerived.python)
    assert(!nestedLines.contains("test"), clue = nestedDerived.python)
    assert(!nestedLines.exists(_.contains("0 = test")), clue = nestedDerived.python)
    assert(nestedDerived.pythonCompatible, clue = (nestedDerived.unsupportedSelectors, nestedDerived.python))

    val sibling =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>test</l><l>0</l></block><block var="test"/></script></scripts></sprite></sprites></stage><variables><variable name="test"><l>0</l></variable></variables></scene></scenes></project>"""
    val siblingDerived = SnapProgramDerivation.fromXml(sibling)
    val siblingLines = nonEmptyLines(siblingDerived.python)
    assert(siblingLines.contains("test: Any = 0"), clue = siblingDerived.python)
    assert(!siblingLines.contains("test"), clue = siblingDerived.python)
    assert(siblingDerived.pythonCompatible, clue = (siblingDerived.unsupportedSelectors, siblingDerived.python))
  }

  test("doSetVar XML roundtrips to python assignment and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>steps</l><l>10</l></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = parse(xml)
    val python = printed(parsed)
    assert(python.contains("steps: Any = 10"), clue = python)
    val xmlOut = written(parsed, "vars")
    assertContainsAll(xmlOut, """s="doSetVar"""", """<l>steps</l>""", """<variable name="steps">""")
    appliedXml(PythonToSnap.applyPython(python))
  }

  test("doChangeVar and nested variable reporter roundtrip") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>steps</l><l>1</l></block><block s="doChangeVar"><l>steps</l><l>2</l></block><block s="doIf"><block s="reportVariadicLessThan"><list><block var="steps"/><l>10</l></list></block><script><block s="forward"><block var="steps"/></block></script></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = parse(xml)
    val python = printed(parsed)
    assertContainsAll(python, "steps: Any = 1", "steps = steps + 2", "if steps < 10:", "forward(steps)")
    val xmlOut = written(parsed, "vars")
    assertContainsAll(xmlOut, """s="doChangeVar"""", """<block var="steps"/>""")
    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml, """s="doChangeVar"""", """<variable name="steps">""")
  }

  test("mixed palette blocks roundtrip Snap XML to python and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="down"></block><block s="doSetVar"><l>steps</l><l>10</l></block><block s="doRepeat"><l>4</l><script><block s="forward"><l>50</l></block><block s="turn"><l>90</l></block></script></block><block s="doIfElse"><block s="reportVariadicLessThan"><list><block var="steps"/><l>20</l></list></block><script><block s="gotoXY"><l>10</l><l>20</l></block></script><script><block s="setHeading"><l>90</l></block></script></block><block s="doUntil"><block s="reportFalse"></block><script><block s="up"></block></script></block><block s="clear"></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""
    val parsed = parse(xml)
    assertSupported(parsed)

    val python = printed(parsed)
    assertContainsAll(python,
      "steps: Any = 10",
      "for _ in range(4):",
      "forward(50)",
      "turtle.right(90)",
      "if steps < 20:",
      "turtle.goto(10, 20)",
      "turtle.setheading(90)",
      "while True:",
      "down()",
      "up()",
      "clear()")

    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml,
      """s="doRepeat"""",
      """s="doIfElse"""",
      """s="doUntil"""",
      """s="gotoXY"""",
      """s="setHeading"""",
      """s="doSetVar"""",
      """s="clear"""")
  }

  test("doWait block roundtrips Snap XML to python and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="doWait"><l>1</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val stored = ProgrammingState.serializer.serialize(ProgrammingStateRaw(SnapLanguage, xml))
    val restored = ProgrammingState.serializer.deserialize(stored)
    assert(restored.toSnapXml.code.contains("""s="doWait""""), clue = restored.toSnapXml.code)

    val parsed = parse(xml)
    assertSupported(parsed)
    val python = printed(parsed)
    assert(python.contains("do_wait(1)"), clue = python)

    val derived = SnapProgramDerivation.fromXml(xml)
    assert(derived.pythonCompatible, clue = derived)
    assert(!derived.unsupportedSelectors.contains("doWait"), clue = derived.unsupportedSelectors)

    assertContainsAll(appliedXml(PythonToSnap.applyPython(python)), """s="doWait"""")
  }

  test("doFor XML roundtrips to python for-range and back to Snap") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doFor"><l>i</l><l>1</l><l>4</l><script><block s="forward"><block s="reportVariadicProduct"><list><block var="i"/><l>10</l></list></block></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = parse(xml)
    assertSupported(parsed)
    val python = printed(parsed)
    assertContainsAll(python, "for i in range(1, 5):", "forward(i * 10)")
    val xmlOut = written(parsed, "dofor")
    assertContainsAll(xmlOut, """s="doFor"""", """s="reportVariadicProduct"""")
    assertContainsAll(appliedXml(PythonToSnap.applyPython(python)), """s="doFor"""")
  }

  test("custom block-definition XML roundtrips with body and call") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="other"><inputs><input type="%n" name="n">n</input></inputs><scripts><script><block s="forward"><block var="n"/></block><block s="turn"><l>90</l></block></script></scripts></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val parsed = parse(xml)
    val python = printed(parsed)
    assertContainsAll(python, "def square", "forward(n)", "square(50)")
    val xmlOut = written(parsed, "square")
    assertContainsAll(xmlOut, """<block-definition s="square %n"""", """<custom-block s="square %n">""")
    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml, """<block-definition s="square %n"""", """<custom-block s="square %n">""")
  }

  test("native Snap block-definition script child prints the function body") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="Variables"><inputs><input type="%n" name="n">n</input></inputs><script><block s="forward"><block var="n"/></block><block s="turn"><l>90</l></block></script></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assertContainsAll(python, "def square(n: float):", "forward(n)", "turtle.right(90)", "square(50)")
  }

  test("native Snap semantic spec plus type-spec call prints one function") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %size" type="command" category="Variables"><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assertContainsAll(python, "def square(size: float):", "forward(size)", "square(50)")
    assertEquals(python.split("def square").length - 1, 1, clue = python)
    assert(SnapExpressionSupport.isPythonCompatibleXml(xml), clue = xml)
    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml, """<block-definition s="square %size"""", """<custom-block s="square %n">""")
  }

  test("setColor setXPosition setYPosition and setSize roundtrip to canonical python names") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="setColor"><color>255,0,0,1</color></block><block s="setXPosition"><l>10</l></block><block s="setYPosition"><l>20</l></block><block s="setSize"><l>3</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assertContainsAll(python, "color(", "turtle.setx(10)", "turtle.sety(20)", "pensize(3)")
    assertContainsNone(python, "set_color", "set_x_position", "set_y_position", "set_size")
    val reXml = appliedXml(PythonToSnap.applyPython(python))
    assertContainsAll(reXml, """s="setColor"""", "<color>", """s="setXPosition"""", """s="setYPosition"""", """s="setSize"""")
  }

  test("python aliases roundtrip through Snap XML") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="turn"><l>90</l></block><block s="turnLeft"><l>15</l></block><block s="gotoXY"><l>1</l><l>2</l></block><block s="up"></block><block s="down"></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assertContainsAll(python, "turtle.right(90)", "turtle.left(15)", "turtle.goto(1, 2)")
    val reXml = appliedXml(PythonToSnap.applyPython(
      """right(90)
        |left(15)
        |goto(1, 2)
        |penup()
        |pendown()
        |""".stripMargin
    ))
    assertContainsAll(reXml, """s="turn"""", """s="turnLeft"""", """s="gotoXY"""", """s="up"""", """s="down"""")
  }

  private def projectWith(definitions: String, scriptBlocks: String): String =
    s"""<project name="t" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="t"><notes></notes><palette><category name="Pen" color="0,161,120,1"/></palette><hidden></hidden><headers></headers><code></code><blocks>$definitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360"><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1"><blocks></blocks><variables></variables><scripts><script x="70" y="80">$scriptBlocks</script></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  test("a snap-built custom block keeps its definition head through a python roundtrip") {
    val definition =
      """<block-definition s="draw square %size" type="command" category="Pen" helper="true"><comment w="90" collapsed="false">draws a square</comment><header></header><code></code><translations></translations><inputs><input type="%s" readonly="true">10</input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition>"""
    val xml = projectWith(
      definition,
      """<block s="receiveGo"></block><custom-block s="draw square %s"><l>10</l></custom-block>"""
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)

    val parsed = parse(xml)
    val python = printed(parsed)
    assertContainsAll(python, "def draw_square(size: Any):", "forward(size)", "draw_square(10)")

    val reXml = appliedXml(PythonToSnap.applyPython(python.replace("forward(size)", "turn(size)"), parsed.canvasLayout, xml))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(reXml), Nil, clue = reXml)

    val before = SnapCustomBlockRules.globalDefinitions(xml).head
    val after = SnapCustomBlockRules.globalDefinitions(reXml).head
    assertEquals(after.spec, before.spec)
    assertEquals(after.blockType, before.blockType)
    assertEquals(after.category, before.category)
    Seq("inputs", "comment").foreach { child =>
      assertEquals(
        after.element.element.named(child).headOption.map(_.element.toXml),
        before.element.element.named(child).headOption.map(_.element.toXml),
        clue = child
      )
    }
    assert(reXml.contains("""helper="true""""), clue = reXml)

    val body = after.bodyScript.map(_.element.toXml).getOrElse("")
    assertContainsAll(body, """s="turn"""", """<block var="size"/>""")
    assertContainsNone(body, """s="forward"""")
  }

  test("two snap blocks sharing a first label word stay two python functions") {
    val definitions =
      """<block-definition s="draw square %size" type="command" category="Pen"><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition>""" +
        """<block-definition s="draw circle %radius" type="command" category="Pen"><inputs><input type="%n"></input></inputs><script><block s="turn"><block var="radius"/></block></script></block-definition>"""
    val xml = projectWith(
      definitions,
      """<block s="receiveGo"></block><custom-block s="draw square %n"><l>10</l></custom-block><custom-block s="draw circle %n"><l>20</l></custom-block>"""
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)

    val parsed = parse(xml)
    val python = printed(parsed)
    assertContainsAll(python,
      "def draw_square(size: float):",
      "def draw_circle(radius: float):",
      "draw_square(10)",
      "draw_circle(20)")

    val reXml = appliedXml(PythonToSnap.applyPython(python, parsed.canvasLayout, xml))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(reXml), Nil, clue = reXml)
    assertEquals(
      SnapCustomBlockRules.globalDefinitions(reXml).map(_.spec).sorted,
      List("draw circle %radius", "draw square %size")
    )
  }

  test("a reporter definition becomes a function returning what it reports, not a command") {
    val xml = projectWith(
      """<block-definition s="area %size" type="reporter" category="Pen"><inputs><input type="%n"></input></inputs><script><block s="doReport"><block s="reportVariadicProduct"><list><block var="size"/><block var="size"/></list></block></block></script></block-definition>""",
      """<block s="receiveGo"></block>"""
    )
    val derived = SnapProgramDerivation.fromXml(xml)
    assert(derived.pythonCompatible, clue = derived)
    assert(derived.python.contains("def area(size: float) -> float:"), derived.python)
    assert(derived.python.contains("return size * size"), derived.python)
    assertEquals(derived.unsupportedSelectors, Nil)
  }

  test("custom block extra arguments are dropped on python apply") {
    val xml =
      """<project><scenes select="1"><scene><blocks><block-definition s="square %n" type="command" category="other"><inputs><input type="%n" name="n">n</input></inputs><scripts><script><block s="forward"><block var="n"/></block></script></scripts></block-definition></blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><custom-block s="square %n"><l>50</l></custom-block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val python = printed(parse(xml))
    assert(python.contains("def square"), clue = python)
    val mismatched = python.stripSuffix("\n") + "\nsquare(1, 2)\n"
    val nextXml = appliedXml(PythonToSnap.applyPython(mismatched))
    assert(nextXml.contains("""<custom-block s="square %n"><l>1</l></custom-block>"""), clue = nextXml)
    assert(!nextXml.contains("<l>2</l>"), clue = nextXml)
  }

  test("boolean literals read the same in every Snap input slot") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doSetVar"><l>flag</l><l><bool>true</bool></l></block><block s="doIf"><block var="flag"/><script><block s="forward"><l>10</l></block></script><list></list></block></script></scripts></sprite></sprites></stage><variables><variable name="flag"><l>0</l></variable></variables></scene></scenes></project>"""
    val python = printed(parse(xml))
    assert(python.contains("flag: Any = True"), clue = python)
    assert(!python.contains("= true"), clue = python)
  }
}
