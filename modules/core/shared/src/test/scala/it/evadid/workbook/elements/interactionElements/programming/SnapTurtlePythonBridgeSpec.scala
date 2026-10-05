package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution, JavaTurtleSemantics, JavaTurtleSource, JavaTurtleStructure, JavaTurtleVmPrograms}
import munit.FunSuite

class SnapTurtlePythonBridgeSpec extends FunSuite {

  test("allowed python names include forward") {
    assert(
      SnapTurtlePythonBridge.AllowedPythonNames.contains("forward"),
      clue = SnapTurtlePythonBridge.AllowedPythonNames.toList.sorted.mkString(",")
    )
    val program = BeProgram.fromPythonString("forward(10)")
    val statements = SnapTurtlePythonBridge.topLevelStatements(program.fullProgram)
    val call = statements.collectFirst { case c: it.evadid.vm.code.usage.BeFunctionCall => c }.get
    assertEquals(SnapTurtlePythonBridge.pythonName(call), "forward")
    val args = SnapControlFlow.orderedArgs(call)
    assert(args.forall(SnapControlFlow.isSupportedValue), clue = args.map(_.getClass.getName).mkString(","))
    assertEquals(SnapTurtlePythonBridge.validateSubset(program.fullProgram), Right(statements))
  }

  test("applyPython accepts turtle-subset calls") {
    val result = SnapTurtlePythonBridge.applyPython(
      """receive_go()
        |forward(50)
        |turn(90)
        |goto_x_y(10, 20)
        |set_heading(90)
        |""".stripMargin
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="receiveGo""""), clue = xml)
    assert(xml.contains("""s="forward""""), clue = xml)
    assert(xml.contains("""s="turn""""), clue = xml)
    assert(xml.contains("""s="gotoXY""""), clue = xml)
    assert(xml.contains("""s="setHeading""""), clue = xml)
  }

  test("applyPython rejects unknown calls") {
    val result = SnapTurtlePythonBridge.applyPython("move(10)")
    assert(result.isLeft, clue = result)
  }

  test("snapSelectorOf maps python snake_case to Snap ids") {
    val program = BeProgram.fromPythonString("goto_x_y(1, 2)\nset_heading(90)\nreceive_go()")
    val selectors =
      SnapTurtlePythonBridge
        .topLevelStatements(program.fullProgram)
        .collect { case c: it.evadid.vm.code.usage.BeFunctionCall => c }
        .map(SnapTurtlePythonBridge.snapSelectorOf)
    assertEquals(selectors, List("gotoXY", "setHeading", "receiveGo"))
    val applied = SnapTurtlePythonBridge.applyPython(
      "goto_x_y(1, 2)\nset_heading(90)\nreceive_go()"
    )
    assert(applied.isRight, clue = applied)
  }

  test("applyPython accepts arithmetic in assignments") {
    val result = SnapTurtlePythonBridge.applyPython(
      """x = 1 * 2
        |forward(10)
        |""".stripMargin
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="reportVariadicProduct""""), clue = xml)
  }

  test("applyPython accepts assignments and variable conditions") {
    val source =
      """i = 1
        |while i < 3:
        |    i = i + 1
        |forward(i)
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val python = SnapTurtlePythonBridge.printedPython(BeProgram.fromPythonString(source).fullProgram)
    assert(python.contains("i = 1"), clue = python)
    assert(python.contains("while i < 3:"), clue = python)
    assert(python.contains("i = i + 1"), clue = python)
    assert(python.contains("forward(i)"), clue = python)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="doSetVar""""), clue = xml)
    assert(xml.contains("""s="doUntil""""), clue = xml)
  }

  test("applyPython accepts arithmetic outside change-variable pattern") {
    val result = SnapTurtlePythonBridge.applyPython("i = 1 * 2")
    assert(result.isRight, clue = result)
    assert(result.toOption.get.snapXml.contains("""s="reportVariadicProduct""""), clue = result.toOption.get.snapXml)
  }

  test("applyPython accepts augmented assignment as change pattern") {
    val source =
      """steps = 1
        |steps += 2
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val python = SnapTurtlePythonBridge.printedPython(BeProgram.fromPythonString(source).fullProgram)
    assert(python.contains("steps = 1"), clue = python)
    assert(python.contains("steps = steps + 2"), clue = python)
    assert(result.toOption.get.snapXml.contains("""s="doChangeVar""""), clue = result.toOption.get.snapXml)
  }

  test("applyPython accepts control-flow subset") {
    val result = SnapTurtlePythonBridge.applyPython(
      """receive_go()
        |for _ in range(2):
        |    forward(10)
        |while not True:
        |    turn(90)
        |if False:
        |    clear()
        |else:
        |    up()
        |""".stripMargin
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="doRepeat""""), clue = xml)
    assert(xml.contains("""s="doUntil""""), clue = xml)
    assert(xml.contains("""s="doIfElse""""), clue = xml)
  }

  test("comparison conditions print as infix python") {
    val source =
      """if 1 < 2:
        |    forward(10)
        |while 3 > 1:
        |    turn(90)
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val python = SnapTurtlePythonBridge.printedPython(BeProgram.fromPythonString(source).fullProgram)
    assert(python.contains("if 1 < 2:"), clue = python)
    assert(python.contains("while 3 > 1:"), clue = python)
    assert(!python.contains("<("), clue = python)
  }

  test("reconcileLayout preserves partitions when callCount matches") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val result = SnapTurtlePythonBridge.applyPython(
      """forward(1)
        |forward(2)
        |turn(3)
        |""".stripMargin,
      layout
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""<script x="70" y="80">"""), clue = xml)
    assert(xml.contains("""<script x="200" y="150">"""), clue = xml)
  }

  test("reconcileLayout resets to single script when callCount changes") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val result = SnapTurtlePythonBridge.applyPython(
      """forward(1)
        |turn(2)
        |""".stripMargin,
      layout
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""<script x="156" y="66">"""), clue = xml)
    assert(!xml.contains("""<script x="70" y="80">"""), clue = xml)
  }

  test("empty python clears scripts") {
    val result = SnapTurtlePythonBridge.applyPython("")
    assert(result.isRight, clue = result)
    assertEquals(result.toOption.get.snapXml, SnapProjectXml.empty)
  }

  test("python to state fingerprint is stable for positional calls") {
    val a = SnapTurtlePythonBridge.applyPython("forward(12345)").toOption.get
    val stored = ProgrammingExercise.StateSerializer.serialize(a)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(stored.contains("12345"), clue = stored)
    assert(stored.startsWith("SNAP_XML_V1"), clue = stored.take(80))
    assertEquals(restored.snapXml, a.snapXml)
    assert(restored.snapXml.contains("""s="forward""""), clue = restored.snapXml)
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
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val python = SnapTurtlePythonBridge.printedPython(BeProgram.fromPythonString(source).fullProgram)
    assert(python.contains("receive_go()"), clue = python)
    assert(python.contains("do_wait(1)"), clue = python)
    assert(python.contains("down()"), clue = python)
    assert(python.contains("steps = 10"), clue = python)
    assert(python.contains("for _ in range(4):"), clue = python)
    assert(python.contains("forward(50)"), clue = python)
    assert(python.contains("turn(90)"), clue = python)
    assert(python.contains("if steps < 20:"), clue = python)
    assert(python.contains("goto_x_y(10, 20)"), clue = python)
    assert(python.contains("set_heading(90)"), clue = python)
    assert(python.contains("while not False:"), clue = python)
    assert(python.contains("up()"), clue = python)
    assert(python.contains("clear()"), clue = python)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="doWait""""), clue = xml)
    assert(xml.contains("""s="doRepeat""""), clue = xml)
    assert(xml.contains("""s="doIfElse""""), clue = xml)
    assert(xml.contains("""s="doUntil""""), clue = xml)
  }

  test("unsupportedSnapSelectors names the wait block and the undefined custom block") {
    val xml =
      """<project><scripts><script><block s="forward"><l>10</l></block><block s="wait"><l>1</l></block><custom-block s="foo"></custom-block></script></scripts></project>"""
    val reported = SnapTurtlePythonBridge.unsupportedSnapSelectors(xml)
    assert(reported.contains("wait"), clue = reported)
    assert(reported.exists(_.contains("'foo'")), clue = reported)
    assert(!SnapTurtlePythonBridge.isPythonCompatibleXml(xml))
    assert(SnapTurtlePythonBridge.isPythonCompatibleXml("""<project><block s="forward"><l>1</l></block></project>"""))
  }

  test("unsupportedSnapSelectors allows a custom-block whose definition declares the same blockSpec") {
    val xml =
      """<project><blocks><block-definition s="square %n" type="command"><inputs><input type="%n"></input></inputs></block-definition></blocks><scripts><script><custom-block s="square %n"><l>50</l></custom-block></script></scripts></project>"""
    assertEquals(SnapTurtlePythonBridge.unsupportedSnapSelectors(xml), Nil)
  }

  test("unsupportedSnapSelectors reports reporter and script-slot custom blocks by spec") {
    val reporter =
      """<project><blocks><block-definition s="area %size" type="reporter"><inputs><input type="%n"></input></inputs></block-definition></blocks></project>"""
    assert(SnapTurtlePythonBridge.unsupportedSnapSelectors(reporter).exists(_.contains("area %size")))
    val scripted =
      """<project><blocks><block-definition s="twice %action" type="command"><inputs><input type="%cs"></input></inputs></block-definition></blocks></project>"""
    assert(SnapTurtlePythonBridge.unsupportedSnapSelectors(scripted).exists(_.contains("%cs")))
  }

  test("snapSelectorOf maps python aliases to Snap ids") {
    val program = BeProgram.fromPythonString("right(90)\nleft(15)\ngoto(1, 2)\npenup()\npendown()\ncolor(\"red\")")
    val selectors =
      SnapTurtlePythonBridge
        .topLevelStatements(program.fullProgram)
        .collect { case c: it.evadid.vm.code.usage.BeFunctionCall => c }
        .map(SnapTurtlePythonBridge.snapSelectorOf)
    assertEquals(selectors, List("turn", "turnLeft", "gotoXY", "up", "down", "setColor"))
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
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="doFor""""), clue = xml)
    assert(xml.contains("""s="reportVariadicProduct""""), clue = xml)
    assert(xml.contains("""s="turnLeft""""), clue = xml)
    assert(xml.contains("""s="runningStitch""""), clue = xml)
    assert(xml.contains("""s="setColor""""), clue = xml)
    assert(xml.contains("""s="home""""), clue = xml)
    val python = SnapTurtlePythonBridge.printedPython(BeProgram.fromPythonString(source).fullProgram)
    assert(python.contains("for i in range(1, 4 + 1):"), clue = python)
    assert(python.contains("forward(i * 10)"), clue = python)
  }

  test("applyPython roundtrips a custom block definition") {
    val source =
      """def square(n):
        |    forward(n)
        |    turn(90)
        |square(50)
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""<block-definition s="square %n""""), clue = xml)
    assert(xml.contains("""category="Variables""""), clue = xml)
    val definition = """(?s)<block-definition[^>]*>.*?</block-definition>""".r.findFirstIn(xml).getOrElse("")
    assert(definition.contains("</inputs><script>"), clue = definition)
    assert(!definition.contains("<scripts>"), clue = definition)
    assert(xml.contains("""<custom-block s="square %n">"""), clue = xml)
    assert(xml.contains("""s="forward""""), clue = xml)
    assert(xml.contains("""<palette><category name="Variables""""), clue = xml)
  }

  test("applyPython names slots in the definition and uses their types on calls") {
    val result = SnapTurtlePythonBridge.applyPython(
      """def square(size):
        |    forward(size)
        |square(50)
        |""".stripMargin
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""<block-definition s="square %size""""), clue = xml)
    assert(xml.contains("""<custom-block s="square %n">"""), clue = xml)
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml).map(_.blockSpec), List("square %n"))
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
    assert(SnapTurtlePythonBridge.isPythonCompatibleXml(xml), clue = xml)
  }

  test("applyPython accepts empty and pass custom-block bodies") {
    val emptyResult = SnapTurtlePythonBridge.applyPython(
      """def square(n):
        |    pass
        |""".stripMargin
    )
    assert(emptyResult.isRight, clue = emptyResult)
    val xml = emptyResult.toOption.get.snapXml
    assert(xml.contains("""<block-definition s="square %n""""), clue = xml)
    assert(xml.contains("""category="Variables""""), clue = xml)
    val definition = """(?s)<block-definition[^>]*>.*?</block-definition>""".r.findFirstIn(xml).getOrElse("")
    assert(!definition.contains("<script>"), clue = definition)
    val python = SnapTurtlePythonBridge.printedPython(
      it.evadid.vm.BeProgram.fromPythonString("def square(n):\n    pass\n").fullProgram
    )
    assert(python.contains("def square(n):"), clue = python)
    assert(python.contains("pass"), clue = python)
  }

  test("applyPython rejects primitive arity mismatches") {
    val tooMany = SnapTurtlePythonBridge.applyPython("forward(1, 2)")
    assert(tooMany.isLeft, clue = tooMany)
    assert(tooMany.swap.toOption.get.contains("forward"), clue = tooMany)
    val tooFew = SnapTurtlePythonBridge.applyPython("goto_x_y(1)")
    assert(tooFew.isLeft, clue = tooFew)
    assert(tooFew.swap.toOption.get.contains("goto_x_y"), clue = tooFew)
  }

  test("applyPython drops extra custom-call arguments to match the def") {
    val source =
      """def square(n):
        |    forward(n)
        |square(1, 2)
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
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
    val result = SnapTurtlePythonBridge.applyPython(
      """set_x(10)
        |set_y(20)
        |pensize(3)
        |color("red")
        |""".stripMargin
    )
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="setXPosition""""), clue = xml)
    assert(xml.contains("""s="setYPosition""""), clue = xml)
    assert(xml.contains("""s="setSize""""), clue = xml)
    assert(xml.contains("""s="setColor""""), clue = xml)
    assert(xml.contains("<color>255,0,0,1</color>"), clue = xml)
  }

  test("applyPython rejects unsupported color literals") {
    val result = SnapTurtlePythonBridge.applyPython("""color("not-a-color")""")
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.toLowerCase.contains("color"), clue = result)
  }

  test("applyPython accepts color aliases and rgb strings") {
    val result = SnapTurtlePythonBridge.applyPython("""pencolor("rgb(0, 128, 0)")""")
    assert(result.isRight, clue = result)
    val xml = result.toOption.get.snapXml
    assert(xml.contains("""s="setColor""""), clue = xml)
    assert(xml.contains("<color>"), clue = xml)
  }

  /** Project XML as Snap writes it, with one global definition and no scripts. */
  private def snapProjectWith(definitions: String): String =
    s"""<project name="t" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="t"><notes></notes><palette><category name="Variables" color="243,118,29,1"/></palette><hidden></hidden><headers></headers><code></code><blocks>$definitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360"><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1"><blocks></blocks><variables></variables><scripts></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  private def applied(python: String, previousXml: String): String = {
    val result = SnapTurtlePythonBridge.applyPython(python, SnapCanvasLayout.empty, previousXml)
    assert(result.isRight, clue = s"$python -> $result")
    val xml = result.toOption.get.snapXml
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
    assertEquals(SnapXmlParser.child(definition.element.inner, "inputs").map(_.outer), Some(inputs))
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
    val definition = """(?s)<block-definition[^>]*>.*?</block-definition>""".r.findFirstIn(xml).getOrElse("")
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
    val result = SnapTurtlePythonBridge.applyPython(
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

  private val javaStartupSelectors = List("receiveGo", "clear", "up", "gotoXY", "setHeading", "down")

  private def javaProgram(source: String): JavaTurtleVmPrograms.Program = {
    val result = for {
      parsed <- JavaTurtleSource.parse(source)
      structured <- JavaTurtleStructure.check(parsed)
      typed <- JavaTurtleSemantics.check(structured)
      resolved <- JavaTurtleResolution.resolve(typed)
      program <- JavaTurtleVmPrograms.adapt(resolved)
    } yield program
    result.fold(problem => fail(s"${problem.message}\n$source"), identity)
  }

  private def javaXml(source: String): String = {
    val result = JavaTurtleSnapXml.render(javaProgram(source))
    val xml = result.fold(problem => fail(s"${problem.message}\n$source"), _.snapXml)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = xml)
    xml
  }

  private def javaMainBlocks(xml: String): List[SnapXmlParser.Element] = {
    val sprite = SnapXmlParser.elements(xml, "sprite").head
    val scripts = SnapXmlParser.child(sprite.inner, "scripts").get
    val children = SnapXmlParser.children(scripts.inner)
    assertEquals(children.map(_.tag), List("script"), clue = xml)
    SnapXmlParser.children(children.head.inner)
  }

  private def javaRejected(source: String, expected: JavaTurtleSnapXml.Problem): Unit = {
    val result = JavaTurtleSnapXml.render(javaProgram(source))
    val diagnostic = result.swap.fold(_ => fail(s"Unexpectedly accepted: $source"), identity)
    assertEquals(diagnostic.problem, expected, clue = source)
    assert(diagnostic.message.nonEmpty, clue = source)
  }

  test("Java XML initializes the turtle before an empty main") {
    val xml = javaXml("class Drawing { public static void main(String[] args) { ; } }")
    val main = javaMainBlocks(xml)
    assertEquals(main.map(_.attrOrEmpty("s")), javaStartupSelectors)
    assertEquals(SnapXmlParser.children(main(3).inner).map(_.inner), List("0", "0"))
    assertEquals(SnapXmlParser.child(main(4).inner, "l").map(_.inner), Some("90"))
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml), Nil)
    assert(!xml.contains("%args"), clue = xml)
  }

  test("Java XML keeps a parametrised square as a custom block") {
    val xml = javaXml(
      """class Drawing {
        |  static void square(int sideLength) {
        |    Turtle.forward(sideLength); Turtle.turnRight(90);
        |    Turtle.forward(sideLength); Turtle.turnRight(90);
        |    Turtle.forward(sideLength); Turtle.turnRight(90);
        |    Turtle.forward(sideLength); Turtle.turnRight(90);
        |  }
        |  public static void main(String[] args) { square(40); square(70); }
        |}""".stripMargin
    )
    val definition = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(definition.spec, "square %sideLength")
    assertEquals(definition.slotNames, List("sideLength"))
    assertEquals(definition.slots.map(_.slotType), List("%n"))
    assertEquals(definition.blockSpec, "square %n")
    val body = SnapXmlParser.children(definition.bodyScript.get.inner)
    assertEquals(body.map(_.attrOrEmpty("s")), List.fill(4)(List("forward", "turn")).flatten)
    assertEquals(SnapXmlParser.elements(definition.element.outer, "block").flatMap(_.attr("var")), List.fill(4)("sideLength"))
    val main = javaMainBlocks(xml)
    assertEquals(main.map(_.attrOrEmpty("s")), javaStartupSelectors ++ List("square %n", "square %n"))
    assertEquals(main.drop(javaStartupSelectors.size).map(block => SnapXmlParser.child(block.inner, "l").get.inner), List("40", "70"))
    assert(!xml.contains("java_variable_"), clue = xml)
    assert(!xml.contains("<variable name=\"sideLength\""), clue = xml)
  }

  test("Java XML finds main between helpers and keeps empty helper bodies") {
    val xml = javaXml(
      """class Drawing {
        |  static void before(int n) { ; }
        |  public static void main(String[] ignored) { before(1); after(); }
        |  static void after() { Turtle.forward(2); }
        |}""".stripMargin
    )
    val definitions = SnapCustomBlockRules.globalDefinitions(xml)
    assertEquals(definitions.map(_.spec), List("before %n", "after"))
    assertEquals(definitions.head.bodyScript, None)
    assertEquals(definitions.last.slots, Nil)
    assertEquals(javaMainBlocks(xml).map(_.attrOrEmpty("s")), javaStartupSelectors ++ List("before %n", "after"))
    assert(!xml.contains("%ignored"), clue = xml)
  }

  test("Java XML preserves grouped literals and both int32 bounds") {
    val xml = javaXml(
      "class Drawing { public static void main(String[] args) { " +
        "Turtle.forward(-2147483648); Turtle.turnRight(2147483647); Turtle.forward(((0))); } }"
    )
    val commands = javaMainBlocks(xml).drop(javaStartupSelectors.size)
    assertEquals(commands.map(_.attrOrEmpty("s")), List("forward", "turn", "forward"))
    assertEquals(commands.map(command => SnapXmlParser.child(command.inner, "l").get.inner),
      List("-2147483648", "2147483647", "0"))
  }

  test("Java XML keeps nested and repeated helper calls with local parameter names") {
    val xml = javaXml(
      """class Drawing {
        |  static void pair(int first, int second) { leaf((second), ((first))); leaf(first, second); }
        |  static void leaf(int size, int angle) { Turtle.forward((size)); Turtle.turnRight(angle); }
        |  public static void main(String[] args) { pair(10, 20); pair(30, 40); }
        |}""".stripMargin
    )
    val definitions = SnapCustomBlockRules.globalDefinitions(xml)
    assertEquals(definitions.map(_.spec), List("pair %first %second", "leaf %size %angle"))
    assertEquals(definitions.map(_.blockSpec), List("pair %n %n", "leaf %n %n"))
    val calls = SnapXmlParser.children(definitions.head.bodyScript.get.inner)
    assertEquals(calls.map(_.attrOrEmpty("s")), List("leaf %n %n", "leaf %n %n"))
    assertEquals(calls.map(call => SnapXmlParser.children(call.inner).map(_.attrOrEmpty("var"))),
      List(List("second", "first"), List("first", "second")))
    assertEquals(SnapXmlParser.elements(definitions.last.element.outer, "block").flatMap(_.attr("var")), List("size", "angle"))
    assertEquals(javaMainBlocks(xml).map(_.attrOrEmpty("s")), javaStartupSelectors ++ List("pair %n %n", "pair %n %n"))
    assert(SnapXmlParser.elements(xml, "variable").isEmpty, clue = xml)
  }

  test("Java XML keeps dollar identifiers and distinguishes helpers from Turtle primitives") {
    val xml = javaXml(
      """class Drawing {
        |  static void draw$(int $distance) { Turtle.forward($distance); }
        |  static void draw_(int distance) { draw$(distance); }
        |  static void forward(int n) { Turtle.turnRight(n); }
        |  public static void main(String[] $args) { draw_(10); forward(90); Turtle.forward(20); }
        |}""".stripMargin
    )
    val definitions = SnapCustomBlockRules.globalDefinitions(xml)
    assertEquals(definitions.map(_.spec), List("draw$ %$distance", "draw_ %distance", "forward %n"))
    assertEquals(definitions.map(_.blockSpec).distinct.size, 3)
    assertEquals(SnapXmlParser.elements(definitions.head.element.outer, "block").flatMap(_.attr("var")), List("$distance"))
    val main = javaMainBlocks(xml)
    assertEquals(main.map(_.attrOrEmpty("s")), javaStartupSelectors ++ List("draw_ %n", "forward %n", "forward"))
    assertEquals(main.map(_.tag), List.fill(javaStartupSelectors.size)("block") ++ List("custom-block", "custom-block", "block"))
  }

  test("Java XML rejects unsupported statements even in unused methods") {
    val bodies = List(
      "return;", "int local = 1;", "int local;", "n = 1;", "n += 1;",
      "if (true) { Turtle.forward(n); }", "if (false) {} else { Turtle.forward(n); }",
      "while (n > 0) { Turtle.forward(n); }", "for (int i = 0; i < 2; i = i + 1) { Turtle.forward(n); }",
      "boolean flag = true && false;"
    )
    bodies.foreach { body =>
      javaRejected(s"class Drawing { static void unused(int n) { Turtle.forward(1); $body } " +
        "public static void main(String[] args) { Turtle.forward(99); } }", JavaTurtleSnapXml.Problem.UnsupportedStatement)
    }
    javaRejected("class Drawing { public static void main(String[] args) { return; } }",
      JavaTurtleSnapXml.Problem.UnsupportedStatement)
  }

  test("Java XML rejects arithmetic instead of changing Java integer semantics") {
    List("+n", "-n", "n + 1", "n - 1", "n * 2", "n / 2", "n % 2", "(n + 1)").foreach { expression =>
      javaRejected(s"class Drawing { static void unused(int n) { Turtle.forward($expression); } " +
        "public static void main(String[] args) {} }", JavaTurtleSnapXml.Problem.UnsupportedExpression)
    }
  }

  test("Java XML rejects boolean helper parameters including unused helpers") {
    for
      parameters <- List("boolean flag", "int n, boolean flag")
      main <- List("", if parameters.startsWith("int") then "choose(1, true || false);" else "choose(true);")
    do javaRejected(s"class Drawing { static void choose($parameters) {} " +
      s"public static void main(String[] args) { $main } }", JavaTurtleSnapXml.Problem.UnsupportedParameter)
  }

  test("Java XML input validation rejects recursion before VM construction") {
    val methods = List(
      "static void again(int n) { again(n); }",
      "static void first(int n) { second(n); } static void second(int n) { first(n); }"
    )
    methods.foreach { helpers =>
      val source = s"class Drawing { $helpers public static void main(String[] args) {} }"
      val parsed = JavaTurtleSource.parse(source).fold(problem => fail(problem.message), identity)
      val structured = JavaTurtleStructure.check(parsed).fold(problem => fail(problem.message), identity)
      val result = JavaTurtleSemantics.check(structured)
      val diagnostic = result.swap.fold(_ => fail(s"Unexpectedly accepted recursion: $source"), identity)
      assertEquals(diagnostic.problem, JavaTurtleSource.Problem.UnsupportedSyntax)
      assert(diagnostic.message.contains("Recursive calls"), clue = diagnostic.message)
    }
  }

  test("Java XML enforces Java execution limits for acyclic helpers") {
    def chain(frames: Int): String = {
      val methods = (0 until frames - 1).map { index =>
        val body = if index == frames - 2 then "Turtle.forward(1);" else s"step${index + 1}();"
        s"static void step$index() { $body }"
      }.mkString(" ")
      s"class Drawing { $methods public static void main(String[] args) { step0(); } }"
    }
    assertEquals(SnapCustomBlockRules.globalDefinitions(javaXml(chain(64))).size, 63)
    javaRejected(chain(65), JavaTurtleSnapXml.Problem.ExecutionLimit)
    val methods = (0 until 16).map { index =>
      val body = if index == 15 then "Turtle.forward(1);" else s"step${index + 1}(); step${index + 1}();"
      s"static void step$index() { $body }"
    }.mkString(" ")
    javaRejected(s"class Drawing { $methods public static void main(String[] args) { step0(); } }",
      JavaTurtleSnapXml.Problem.ExecutionLimit)
  }

  test("Java XML is deterministic, survives state storage and leaves Python conversion unchanged") {
    val source = "class Drawing { static void step(int n) { Turtle.forward(n); } " +
      "public static void main(String[] args) { step(40); } }"
    val python = "def square(size):\n    forward(size)\n    turn(90)\n\nsquare(50)\n"
    val before = SnapTurtlePythonBridge.applyPython(python).toOption.get
    val program = javaProgram(source)
    val state = JavaTurtleSnapXml.render(program).toOption.get
    assertEquals(JavaTurtleSnapXml.render(program), Right(state))
    assertEquals(javaXml("/* unchanged source */\n" + source), state.snapXml)
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    assert(stored.startsWith("SNAP_XML_V1"), clue = stored.take(80))
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(stored), state)
    assertEquals(SnapTurtlePythonBridge.applyPython(python), Right(before))
  }
}
