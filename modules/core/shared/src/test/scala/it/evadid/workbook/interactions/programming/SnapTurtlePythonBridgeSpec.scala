package it.evadid.workbook.interactions.programming

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.pythonsnap.PythonToSnap
import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.toXml
import it.evadid.workbook.interactions.programming.state.*
import it.evadid.workbook.interactions.programming.state.snap.*

import it.evadid.workbook.interactions.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}
import munit.FunSuite

class SnapExpressionSupportSpec extends FunSuite {

  /** Snap XML converted from `python`; fails the test when the conversion is rejected. */
  private def snapXml(python: String, layout: SnapCanvasLayout = SnapCanvasLayout.empty): String = {
    val result = PythonToSnap.applyPython(python, layout)
    assert(result.isRight, clue = result)
    result.toOption.get.code
  }

  private def printedPython(source: String): String =
    ExpressionToPython.printedPython(SourceParsing.parse(source, SourceLanguage.Python).parsed().program.fullProgram)

  private def snapSelectors(source: String): List[String] =
    SnapExpressionSupport
      .topLevelStatements(SourceParsing.parse(source, SourceLanguage.Python).parsed().program.fullProgram)
      .collect { case c: it.evadid.vm.code.usage.BeFunctionCall => c }
      .map(SnapExpressionSupport.snapSelectorOf)

  private def firstDefinition(xml: String): String =
    """(?s)<block-definition[^>]*>.*?</block-definition>""".r.findFirstIn(xml).getOrElse("")

  private def assertContains(text: String, fragments: String*): Unit =
    fragments.foreach(fragment => assert(text.contains(fragment), clue = text))

  /** Asserts a block with each Snap selector, written `s="selector"`. */
  private def assertBlocks(xml: String, selectors: String*): Unit =
    assertContains(xml, selectors.map(selector => s"""s="$selector"""")*)

  test("allowed python names include forward") {
    assert(
      SnapTurtleCatalog.AllowedPythonNames.contains("forward"),
      clue = SnapTurtleCatalog.AllowedPythonNames.toList.sorted.mkString(",")
    )
    val program = SourceParsing.parse("forward(10)", SourceLanguage.Python).parsed().program
    val statements = SnapExpressionSupport.topLevelStatements(program.fullProgram)
    val call = statements.collectFirst { case c: it.evadid.vm.code.usage.BeFunctionCall => c }.get
    assertEquals(SnapExpressionSupport.pythonName(call), "forward")
    val args = call.arguments
    assert(args.forall(SnapControlFlow.isSupportedValue(_)), clue = args.map(_.getClass.getName).mkString(","))
    assertEquals(SnapExpressionSupport.validateSubset(program.fullProgram), Right(statements))
  }

  test("applyPython accepts turtle-subset calls") {
    val xml = snapXml(
      """receive_go()
        |forward(50)
        |turn(90)
        |goto_x_y(10, 20)
        |set_heading(90)
        |""".stripMargin
    )
    assertBlocks(xml, "receiveGo", "forward", "turn", "gotoXY", "setHeading")
  }

  test("applyPython puts all Python statements below one green-flag hat") {
    val xml = snapXml("forward(10)\nturn(90)")
    val script = """(?s)<script x="156" y="66">(.*?)</script>""".r
      .findFirstMatchIn(xml)
      .map(_.group(1))
      .getOrElse(fail(s"Missing generated script in $xml"))

    assert(script.startsWith("""<block s="receiveGo"></block>"""), clue = script)
    assertEquals("""s="receiveGo""".r.findAllIn(script).size, 1)
    assert(script.indexOf("""s="receiveGo"""") < script.indexOf("""s="forward""""), clue = script)
    assert(script.indexOf("""s="forward"""") < script.indexOf("""s="turn""""), clue = script)
  }

  test("green-flag hat is omitted when Snap generated from Python is converted back") {
    val snap = ProgrammingStateRaw(Python, "forward(10)\nturn(90)").toSnapXml
    val python = snap.toPython.code

    assert(!python.contains("receive_go"), clue = python)
    assertContains(python, "forward(10)", "right(90)")
  }

  test("applyPython rejects unknown calls") {
    val result = PythonToSnap.applyPython("move(10)")
    assert(result.isLeft, clue = result)
  }

  test("snapSelectorOf maps python snake_case to Snap ids") {
    val source = "goto_x_y(1, 2)\nset_heading(90)\nreceive_go()"
    assertEquals(snapSelectors(source), List("gotoXY", "setHeading", "receiveGo"))
    snapXml(source)
  }

  test("applyPython accepts arithmetic in assignments") {
    val xml = snapXml(
      """x = 1 * 2
        |forward(10)
        |""".stripMargin
    )
    assertBlocks(xml, "reportVariadicProduct")
  }

  test("applyPython accepts assignments and variable conditions") {
    val source =
      """i = 1
        |while i < 3:
        |    i = i + 1
        |forward(i)
        |""".stripMargin
    val xml = snapXml(source)
    assertContains(printedPython(source), "i: float = 1", "while i < 3:", "i = i + 1", "forward(i)")
    assertBlocks(xml, "doSetVar", "doUntil")
  }

  test("applyPython accepts augmented assignment as change pattern") {
    val source =
      """steps = 1
        |steps += 2
        |""".stripMargin
    val xml = snapXml(source)
    assertContains(printedPython(source), "steps: float = 1", "steps = steps + 2")
    assertBlocks(xml, "doChangeVar")
  }

  test("comparison conditions print as infix python") {
    val source =
      """if 1 < 2:
        |    forward(10)
        |while 3 > 1:
        |    turn(90)
        |""".stripMargin
    snapXml(source)
    val python = printedPython(source)
    assertContains(python, "if 1 < 2:", "while 3 > 1:")
    assert(!python.contains("<("), clue = python)
  }

  test("reconcileLayout preserves partitions when callCount matches") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val xml = snapXml(
      """forward(1)
        |forward(2)
        |turn(3)
        |""".stripMargin,
      layout
    )
    assertContains(xml, """<script x="70" y="80"><block s="receiveGo"></block>""",
      """<script x="200" y="150"><block s="receiveGo"></block>""")
    assertEquals("""s="receiveGo""".r.findAllIn(xml).size, 2)
  }

  test("reconcileLayout resets to single script when callCount changes") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val xml = snapXml(
      """forward(1)
        |turn(2)
        |""".stripMargin,
      layout
    )
    assert(xml.contains("""<script x="156" y="66">"""), clue = xml)
    assert(!xml.contains("""<script x="70" y="80">"""), clue = xml)
  }

  test("empty python clears scripts") {
    assertEquals(snapXml(""), SnapProjectXml.empty)
  }

  test("python to state fingerprint is stable for positional calls") {
    val a = PythonToSnap.applyPython("forward(12345)").toOption.get
    val stored = ProgrammingState.serializer.serialize(a)
    val restored = ProgrammingState.serializer.deserialize(stored)
    assert(stored.contains("12345"), clue = stored)
    assertEquals(restored.toSnapXml.code, a.code)
    assert(restored.toSnapXml.code.contains("""s="forward""""), clue = restored.toSnapXml.code)
  }

  test("applyPython accepts full python-compatible palette program") {
    val source =
      """receive_go()
        |do_wait(1)
        |down()
        |steps = 10
        |for _ in range(4):
        |    forward(50)
        |    turn(90)
        |if steps < 20:
        |    goto_x_y(10, 20)
        |else:
        |    set_heading(90)
        |while not False:
        |    up()
        |clear()
        |""".stripMargin
    val xml = snapXml(source)
    val python = printedPython(source)
    assertContains(python, "receive_go()", "do_wait(1)")
    assert(python.contains("turtle.pendown()") && python.contains("turtle.penup()"), clue = python)
    assert(!python.contains("pen_up") && !python.contains("pen_down"), clue = "Python's turtle module spells pen commands penup/pendown")
    assertContains(python, "steps: float = 10", "for _ in range(4):", "forward(50)", "right(90)", "if steps < 20:",
      "goto(10, 20)", "setheading(90)", "while not False:", "up()", "clear()")
    assertBlocks(xml, "doWait", "doRepeat", "doIfElse", "doUntil")
  }

  test("unsupportedSnapSelectors names the wait block and the undefined custom block") {
    val xml =
      """<project><scripts><script><block s="forward"><l>10</l></block><block s="wait"><l>1</l></block><custom-block s="foo"></custom-block></script></scripts></project>"""
    val reported = SnapExpressionSupport.unsupportedSnapSelectors(xml)
    assert(reported.contains("wait"), clue = reported)
    assert(reported.exists(_.contains("'foo'")), clue = reported)
    assert(!SnapExpressionSupport.isPythonCompatibleXml(xml))
    assert(SnapExpressionSupport.isPythonCompatibleXml("""<project><scripts><script><block s="forward"><l>1</l></block></script></scripts></project>"""))
    // A block outside any script is not part of the program, so it is reported instead of dropped.
    assertEquals(SnapExpressionSupport.unsupportedSnapSelectors("""<project><block s="forward"><l>1</l></block></project>"""),
      List("'forward' outside a script"))
  }

  test("unsupportedSnapSelectors allows a custom-block whose definition declares the same blockSpec") {
    val xml =
      """<project><blocks><block-definition s="square %n" type="command"><inputs><input type="%n"></input></inputs></block-definition></blocks><scripts><script><custom-block s="square %n"><l>50</l></custom-block></script></scripts></project>"""
    assertEquals(SnapExpressionSupport.unsupportedSnapSelectors(xml), Nil)
  }

  test("unsupportedSnapSelectors reports hat and script-slot custom blocks by spec, not reporters") {
    val hat =
      """<project><blocks><block-definition s="when ready" type="hat"><inputs></inputs></block-definition></blocks></project>"""
    assert(SnapExpressionSupport.unsupportedSnapSelectors(hat).exists(_.contains("when ready")))
    val reporter =
      """<project><blocks><block-definition s="area %size" type="reporter"><inputs><input type="%n"></input></inputs></block-definition></blocks></project>"""
    assertEquals(SnapExpressionSupport.unsupportedSnapSelectors(reporter), Nil)
    val scripted =
      """<project><blocks><block-definition s="twice %action" type="command"><inputs><input type="%cs"></input></inputs></block-definition></blocks></project>"""
    assert(SnapExpressionSupport.unsupportedSnapSelectors(scripted).exists(_.contains("%cs")))
  }

  test("snapSelectorOf maps python aliases to Snap ids") {
    assertEquals(snapSelectors("right(90)\nleft(15)\ngoto(1, 2)\npenup()\npendown()\ncolor(\"red\")"),
      List("turn", "turnLeft", "gotoXY", "up", "down", "setColor"))
  }

  test("a counted loop with an unused variable survives blocks and back") {
    val source =
      """def circ(n):
        |    edges = 4
        |    for _ in range(edges):
        |        forward(n / edges)
        |        turn_right(360 / edges)
        |""".stripMargin
    val xml = snapXml(source)
    // An unused counter is a repeat; its count may be computed, here a variable.
    assert(xml.contains("""<block s="doRepeat"><block var="edges"/>"""), clue = xml)
    val python = printedPython(source)
    assert(python.contains("for _ in range(edges):"), clue = python)
    assert(!python.contains("for 0 in range"), clue = python)
  }

  test("applyPython accepts named for-range, arithmetic args, and stitches") {
    val source =
      """receive_go()
        |running_stitch(10)
        |for i in range(1, 4 + 1):
        |    forward(i * 10)
        |    turn_left(90)
        |color("red")
        |home()
        |""".stripMargin
    val xml = snapXml(source)
    assertBlocks(xml, "doFor", "reportVariadicProduct", "turnLeft", "runningStitch", "setColor", "home")
    assertContains(printedPython(source), "for i in range(1, 5):", "forward(i * 10)")
  }

  test("applyPython roundtrips a custom block definition") {
    val source =
      """def square(n):
        |    forward(n)
        |    turn(90)
        |square(50)
        |""".stripMargin
    val xml = snapXml(source)
    assertContains(xml, """<block-definition s="square %n"""", """category="Variables"""")
    val definition = firstDefinition(xml)
    assert(definition.contains("</inputs><script>"), clue = definition)
    assert(!definition.contains("<scripts>"), clue = definition)
    assertContains(xml, """<custom-block s="square %n">""", """s="forward"""", """<palette><category name="Variables"""")
  }

  test("applyPython names slots in the definition and uses their types on calls") {
    val xml = snapXml(
      """def square(size):
        |    forward(size)
        |square(50)
        |""".stripMargin
    )
    assertContains(xml, """<block-definition s="square %size"""", """<custom-block s="square %n">""")
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml).map(_.blockSpec), List("square %n"))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
    assert(SnapExpressionSupport.isPythonCompatibleXml(xml), clue = xml)
  }

  test("applyPython accepts empty and pass custom-block bodies") {
    val xml = snapXml(
      """def square(n):
        |    pass
        |""".stripMargin
    )
    assertContains(xml, """<block-definition s="square %n"""", """category="Variables"""")
    val definition = firstDefinition(xml)
    assert(!definition.contains("<script>"), clue = definition)
    assertContains(printedPython("def square(n):\n    pass\n"), "def square(n: Any):", "pass")
  }

  test("applyPython rejects primitive arity mismatches") {
    val tooMany = PythonToSnap.applyPython("forward(1, 2)")
    assert(tooMany.isLeft, clue = tooMany)
    assert(tooMany.swap.toOption.get.contains("forward"), clue = tooMany)
    val tooFew = PythonToSnap.applyPython("goto_x_y(1)")
    assert(tooFew.isLeft, clue = tooFew)
    assert(tooFew.swap.toOption.get.contains("goto"), clue = tooFew)
  }

  test("applyPython drops extra custom-call arguments to match the def") {
    val source =
      """def square(n):
        |    forward(n)
        |square(1, 2)
        |""".stripMargin
    val xml = snapXml(source)
    assert(xml.contains("""<custom-block s="square %n"><l>1</l></custom-block>"""), clue = xml)
    assert(!xml.contains("<l>2</l>"), clue = xml)
  }

  test("applyPython drops a custom-call argument when the def loses that parameter") {
    val previous = snapProjectWith(
      """<block-definition s="circ %dist" type="command" category="Variables"><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="dist"/></block></script></block-definition>"""
    )
    val xml = applied(
      """def circ():
        |    forward(10)
        |
        |circ(50)
        |""".stripMargin,
      previous
    )
    assert(xml.contains("""s="circ" type="command""""), clue = xml)
    assert(xml.contains("""<custom-block s="circ"></custom-block>"""), clue = xml)
    assert(!xml.contains("""<custom-block s="circ %n">"""), clue = xml)
    assert(!xml.contains("%dist"), clue = xml)
  }

  test("applyPython maps canonical pen and position names and native colors") {
    val xml = snapXml(
      """set_x(10)
        |set_y(20)
        |pensize(3)
        |color("red")
        |""".stripMargin
    )
    assertBlocks(xml, "setXPosition", "setYPosition", "setSize", "setColor")
    assert(xml.contains("<color>255,0,0,1</color>"), clue = xml)
  }

  test("applyPython rejects unsupported color literals") {
    val result = PythonToSnap.applyPython("""color("not-a-color")""")
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.toLowerCase.contains("color"), clue = result)
  }

  test("applyPython accepts color aliases and rgb strings") {
    val xml = snapXml("""pencolor("rgb(0, 128, 0)")""")
    assertBlocks(xml, "setColor")
    assert(xml.contains("<color>"), clue = xml)
  }

  /** Project XML as Snap writes it, with one global definition and no scripts. */
  private def snapProjectWith(definitions: String): String =
    s"""<project name="t" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="t"><notes></notes><palette><category name="Variables" color="243,118,29,1"/></palette><hidden></hidden><headers></headers><code></code><blocks>$definitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360"><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1"><blocks></blocks><variables></variables><scripts></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  private def applied(python: String, previousXml: String): String = {
    val result = PythonToSnap.applyPython(python, SnapCanvasLayout.empty, previousXml)
    assert(result.isRight, clue = s"$python -> $result")
    val xml = result.toOption.get.code
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)
    xml
  }

  test("applyPython keeps a multi-word Snap label and its declared slot type") {
    val previous = snapProjectWith(
      """<block-definition s="draw square %size" type="command" category="Variables"><header></header><code></code><translations></translations><inputs><input type="%s"></input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition>"""
    )
    val xml = applied("def draw_square(size):\n    forward(size)\n\ndraw_square(10)\n", previous)
    assert(xml.contains("""<block-definition s="draw square %size""""), clue = xml)
    assert(xml.contains("""<input type="%s">"""), clue = xml)
    assert(xml.contains("""<custom-block s="draw square %s">"""), clue = xml)
  }

  test("applyPython keeps category, comment and loose block-editor scripts") {
    val previous = snapProjectWith(
      """<block-definition s="square %size" type="command" category="Pen" helper="true"><comment w="90" collapsed="false">explains the block</comment><header></header><code></code><translations></translations><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="size"/></block></script><scripts><script x="10" y="20"><block s="clear"></block></script></scripts></block-definition>"""
    )
    val xml = applied("def square(size):\n    turn(90)\n\nsquare(10)\n", previous)
    assert(xml.contains("""category="Pen""""), clue = xml)
    assert(xml.contains("""helper="true""""), clue = xml)
    assert(xml.contains("explains the block"), clue = xml)
    assert(xml.contains("""<scripts><script x="10" y="20">"""), clue = xml)
    assert(xml.contains("""s="turn""""), clue = xml)
  }

  test("applyPython keeps snap slot names when python uses the same names") {
    val previous = snapProjectWith(
      """<block-definition s="square %laenge" type="command" category="Variables"><inputs><input type="%n"></input></inputs></block-definition>"""
    )
    val xml = applied("def square(laenge):\n    forward(laenge)\n\nsquare(10)\n", previous)
    assert(xml.contains("""<block-definition s="square %laenge""""), clue = xml)
    assert(xml.contains("""<block var="laenge"/>"""), clue = xml)
  }

  test("applyPython renames snap slots when python parameters are renamed") {
    val previous = snapProjectWith(
      """<block-definition s="circ %&apos;dist&apos;" type="command" category="Variables"><inputs><input type="%n"></input></inputs><script><block s="forward"><block var="dist"/></block></script></block-definition>"""
    )
    val xml = applied("def circ(radius):\n    forward(radius)\n\ncirc(10)\n", previous)
    assert(xml.contains("""<block-definition s="circ %radius""""), clue = xml)
    assert(xml.contains("""<block var="radius"/>"""), clue = xml)
    assert(!xml.contains("""<block var="dist"/>"""), clue = xml)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)
  }

  test("applyPython rename still declares input types when previous inputs were empty") {
    val previous = snapProjectWith(
      """<block-definition s="circ %'varb'" type="command" category="Variables"><inputs></inputs><script><block s="forward"><block var="varb"/></block></script></block-definition>"""
    )
    val xml = applied("def circ(n):\n    forward(n)\n\ncirc(10)\n", previous)
    val defn = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(defn.spec, "circ %n")
    assertEquals(defn.slots.map(_.slotType), List("%n"), clue = xml)
    assertEquals(defn.blockSpec, "circ %n")
    assert(xml.contains("""<input type="%n">"""), clue = xml)
  }

  test("applyPython declares missing slots for unchanged and renamed parameters") {
    val containers = List("", "<inputs/>", "<inputs></inputs>", "<inputs>\n  </inputs>")
    for
      inputs <- containers
      (parameter, expectedType) <- List("size" -> "%s", "distance" -> "%n")
    do
      val previous = snapProjectWith(
        s"""<block-definition s="draw %size" type="command" category="Variables">$inputs</block-definition>"""
      )
      val xml = applied(s"def draw($parameter):\n    forward($parameter)\n\ndraw(10)\n", previous)
      val definition = SnapCustomBlockRules.globalDefinitions(xml).head
      assertEquals(definition.slots.map(_.slotType), List(expectedType), clue = xml)
      assertEquals(definition.blockSpec, s"draw $expectedType", clue = xml)
      val reapplied = applied(s"def draw($parameter):\n    forward($parameter)\n\ndraw(10)\n", xml)
      assertEquals(SnapCustomBlockRules.globalDefinitions(reapplied).head.slots.map(_.slotType), List(expectedType))
  }

  test("applyPython preserves declared slot metadata when parameters are renamed") {
    val inputs = """<inputs><input type="%s" readonly="true" irreplaceable="true">10<options>10&#10;20</options></input></inputs>"""
    val previous = snapProjectWith(
      s"""<block-definition s="draw %size" type="command" category="Variables">$inputs</block-definition>"""
    )
    val xml = applied("def draw(distance):\n    forward(distance)\n\ndraw(10)\n", previous)
    val definition = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(definition.spec, "draw %distance")
    assertEquals(definition.slots.map(_.slotType), List("%s"))
    assertEquals(definition.element.element.named("inputs").headOption.map(_.element.toXml), Some(inputs))
  }

  test("applyPython keeps parameterless definitions without input slots") {
    for inputs <- List("", "<inputs/>", "<inputs></inputs>", "<inputs>\n  </inputs>") do
      val previous = snapProjectWith(
        s"""<block-definition s="draw" type="command" category="Variables">$inputs</block-definition>"""
      )
      val xml = applied("def draw():\n    forward(10)\n\ndraw()\n", previous)
      val definition = SnapCustomBlockRules.globalDefinitions(xml).head
      assertEquals(definition.slots, Nil, clue = xml)
      assertEquals(definition.blockSpec, "draw")
  }

  test("applyPython keeps the label but rebuilds the spec when the parameter count changes") {
    val previous = snapProjectWith(
      """<block-definition s="draw square %size" type="command" category="Variables"><inputs><input type="%s"></input></inputs></block-definition>"""
    )
    val xml = applied("def draw_square(size, times):\n    forward(size)\n\ndraw_square(10, 2)\n", previous)
    assert(xml.contains("""<block-definition s="draw square %size %times""""), clue = xml)
    assert(xml.contains("""<custom-block s="draw square %s %n">"""), clue = xml)
  }

  test("applyPython adds a slot to a snap block that had none, and calls still resolve") {
    val previous = snapProjectWith(
      """<block-definition s="square" type="command" category="Variables" selector="evaluateCustomBlock"><header></header><code></code><translations></translations><inputs></inputs><script><block s="forward"><l>10</l></block></script><scripts><script x="10" y="20"><custom-block s="square"></custom-block></script></scripts></block-definition>"""
    )
    val xml = applied("def square(n):\n    forward(n)\n\nsquare(50)\n", previous)
    val defn = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(defn.spec, "square %n")
    assertEquals(defn.slots.map(_.slotType), List("%n"))
    assertEquals(defn.blockSpec, "square %n")
    assert(xml.contains("""<custom-block s="square %n">"""), clue = xml)
    assert(xml.contains("""<block var="n"/>"""), clue = xml)
    assert(!xml.contains("""selector="""), clue = xml)
    val definition = firstDefinition(xml)
    assert(!definition.contains("<scripts>"), clue = definition)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)
  }

  test("applyPython still writes a definition when the python has only a def") {
    val previous = snapProjectWith(
      """<block-definition s="square" type="command" category="Variables"><inputs></inputs></block-definition>"""
    )
    val xml = applied("def square(n):\n    forward(n)\n", previous)
    assert(xml.contains("""<block-definition s="square %n""""), clue = xml)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)
  }

  test("applyPython drops a definition once its def is gone from the python source") {
    val previous = snapProjectWith(
      """<block-definition s="square %size" type="command" category="Variables"><inputs><input type="%n"></input></inputs></block-definition>"""
    )
    val xml = applied("forward(10)\n", previous)
    assert(!xml.contains("<block-definition"), clue = xml)
  }

  test("applyPython rejects two defs that would share one block") {
    val result = PythonToSnap.applyPython(
      """def square(n):
        |    forward(n)
        |def square(n):
        |    turn(n)
        |square(1)
        |""".stripMargin
    )
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.contains("square"), clue = result)
  }

  test("applyPython hoists a nested def so its calls still resolve") {
    val xml = applied("for _ in range(2):\n    def square(n):\n        forward(n)\n    square(10)\n", "")
    assert(xml.contains("""<block-definition s="square %n""""), clue = xml)
  }

  test("applyPython declares a new assignment as a scene global") {
    val xml = applied("test = 0\n", "")
    assert(xml.contains("""s="doSetVar""""), clue = xml)
    assert(xml.contains("""<l>test</l>"""), clue = xml)
    val sceneGlobals = """</stage><variables>(.*?)</variables>""".r.findFirstMatchIn(xml).map(_.group(1)).getOrElse("")
    assert(sceneGlobals.contains("""<variable name="test"><l>0</l></variable>"""), clue = xml)
    val withParameter = applied("def square(size):\n    forward(size)\n\nsquare(10)\n", "")
    assert(!withParameter.contains("""<variable name="size">"""), clue = withParameter)
  }

  test("applyPython does not declare function parameters as scene variables") {
    val xml = applied("def square(size):\n    forward(size)\n\nsquare(10)\n", "")
    assert(!xml.contains("""<variable name="size">"""), clue = xml)
    val withGlobal = applied("steps = 5\n\ndef square(size):\n    forward(size)\n\nsquare(steps)\n", "")
    assert(withGlobal.contains("""<variable name="steps">"""), clue = withGlobal)
    assert(!withGlobal.contains("""<variable name="size">"""), clue = withGlobal)
  }
}
