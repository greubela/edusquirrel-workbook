package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
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
    assert(stored.startsWith("PROGRAMMING_STATE_V2\nSNAP_XML"), clue = stored.take(80))
    assertEquals(restored.toSnapXml.snapXml, a.snapXml)
    assert(restored.toSnapXml.snapXml.contains("""s="forward""""), clue = restored.toSnapXml.snapXml)
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

  // Re-enable after the custom-block schema migration can infer missing legacy input metadata.
  test("applyPython rename still declares input types when previous inputs were empty".ignore) {
    val previous = snapProjectWith(
      """<block-definition s="circ %'varb'" type="command" category="Variables"><inputs></inputs><script><block s="forward"><block var="varb"/></block></script></block-definition>"""
    )
    val xml = applied("def circ(n):\n    forward(n)\n", previous)
    val defn = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(defn.spec, "circ %n")
    assertEquals(defn.slots.map(_.slotType), List("%n"), clue = xml)
    assertEquals(defn.blockSpec, "circ %n")
    assert(xml.contains("""<input type="%n">"""), clue = xml)
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
}
