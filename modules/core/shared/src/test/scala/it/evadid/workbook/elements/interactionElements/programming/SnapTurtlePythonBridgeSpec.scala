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

  test("unsupportedSnapSelectors reports wait and unknown custom blocks") {
    val xml =
      """<project><scripts><script><block s="forward"><l>10</l></block><block s="wait"><l>1</l></block><custom-block s="foo"></custom-block></script></scripts></project>"""
    assertEquals(
      SnapTurtlePythonBridge.unsupportedSnapSelectors(xml).toSet,
      Set("wait", "custom-block")
    )
    assert(!SnapTurtlePythonBridge.isPythonCompatibleXml(xml))
    assert(SnapTurtlePythonBridge.isPythonCompatibleXml("""<project><block s="forward"><l>1</l></block></project>"""))
  }

  test("unsupportedSnapSelectors allows custom-block when a definition is present") {
    val xml =
      """<project><blocks><block-definition s="square %n" type="command"></block-definition></blocks><scripts><script><custom-block s="square %n"><l>50</l></custom-block></script></scripts></project>"""
    assertEquals(SnapTurtlePythonBridge.unsupportedSnapSelectors(xml), Nil)
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

  test("applyPython uses semantic definition spec and type spec on calls") {
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
    assertEquals(SnapTurtleCatalog.typeSpecFromSemantic("square %size"), "square %n")
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

  test("applyPython rejects custom-call arity mismatches") {
    val source =
      """def square(n):
        |    forward(n)
        |square(1, 2)
        |""".stripMargin
    val result = SnapTurtlePythonBridge.applyPython(source)
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.contains("square"), clue = result)
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
}
