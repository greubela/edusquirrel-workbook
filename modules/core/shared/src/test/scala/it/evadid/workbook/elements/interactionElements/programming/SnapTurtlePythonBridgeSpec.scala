package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import munit.FunSuite

class SnapTurtlePythonBridgeSpec extends FunSuite {

  private val previousEmpty = ProgrammingExerciseState.mini.copy(canvasLayout = SnapCanvasLayout.empty)

  test("applyPython accepts turtle-subset calls") {
    val result = SnapTurtlePythonBridge.applyPython(
      """receive_go()
        |forward(50)
        |turn(90)
        |goto_x_y(10, 20)
        |set_heading(90)
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val state = result.toOption.get
    val names = SnapTurtlePythonBridge.topLevelCalls(state.program.fullProgram).map(SnapTurtlePythonBridge.pythonName)
    assertEquals(names, List("receive_go", "forward", "turn", "goto_x_y", "set_heading"))
  }

  test("applyPython rejects unknown calls") {
    val result = SnapTurtlePythonBridge.applyPython("move(10)", previousEmpty)
    assert(result.isLeft, clue = result)
  }

  test("snapSelectorOf maps python snake_case to Snap ids") {
    val state = SnapTurtlePythonBridge
      .applyPython("goto_x_y(1, 2)\nset_heading(90)\nreceive_go()", previousEmpty)
      .toOption
      .get
    val selectors =
      SnapTurtlePythonBridge.topLevelCalls(state.program.fullProgram).map(SnapTurtlePythonBridge.snapSelectorOf)
    assertEquals(selectors, List("gotoXY", "setHeading", "receiveGo"))
  }

  test("applyPython rejects unsupported constructs") {
    val result = SnapTurtlePythonBridge.applyPython(
      """x = 1 * 2
        |forward(10)
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.toLowerCase.contains("unsupported"), clue = result)
  }

  test("applyPython accepts assignments and variable conditions") {
    val result = SnapTurtlePythonBridge.applyPython(
      """i = 1
        |while i < 3:
        |    i = i + 1
        |forward(i)
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val python = ProgrammingExerciseState.pythonOf(result.toOption.get)
    assert(python.contains("i = 1"), clue = python)
    assert(python.contains("while i < 3:"), clue = python)
    assert(python.contains("i = i + 1"), clue = python)
    assert(python.contains("forward(i)"), clue = python)
  }

  test("applyPython rejects arithmetic outside change-variable pattern") {
    val result = SnapTurtlePythonBridge.applyPython("i = 1 * 2", previousEmpty)
    assert(result.isLeft, clue = result)
  }

  test("applyPython accepts augmented assignment as change pattern") {
    val result = SnapTurtlePythonBridge.applyPython(
      """steps = 1
        |steps += 2
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val python = ProgrammingExerciseState.pythonOf(result.toOption.get)
    assert(python.contains("steps = 1"), clue = python)
    assert(python.contains("steps = steps + 2"), clue = python)
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
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val statements = SnapTurtlePythonBridge.topLevelStatements(result.toOption.get.program.fullProgram)
    assert(statements.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeRepeatNr]))
    assert(statements.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeWhile]))
    assert(statements.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeIfElse]))
  }

  test("comparison conditions print as infix python") {
    val result = SnapTurtlePythonBridge.applyPython(
      """if 1 < 2:
        |    forward(10)
        |while 3 > 1:
        |    turn(90)
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val python = ProgrammingExerciseState.pythonOf(result.toOption.get)
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
    val previous = ProgrammingExerciseState(BeProgram.miniProgram(), layout)
    val result = SnapTurtlePythonBridge.applyPython(
      """forward(1)
        |forward(2)
        |turn(3)
        |""".stripMargin,
      previous
    )
    assert(result.isRight, clue = result)
    assertEquals(result.toOption.get.canvasLayout.scripts, layout.scripts)
  }

  test("reconcileLayout resets to single script when callCount changes") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val previous = ProgrammingExerciseState(BeProgram.miniProgram(), layout)
    val result = SnapTurtlePythonBridge.applyPython(
      """forward(1)
        |turn(2)
        |""".stripMargin,
      previous
    )
    assert(result.isRight, clue = result)
    assertEquals(
      result.toOption.get.canvasLayout.scripts,
      List(SnapCanvasScript(156, 66, 2))
    )
  }

  test("empty python clears layout") {
    val previous = ProgrammingExerciseState(
      BeProgram.miniProgram(),
      SnapCanvasLayout.single(70, 80, 2)
    )
    val result = SnapTurtlePythonBridge.applyPython("", previous)
    assert(result.isRight, clue = result)
    assert(result.toOption.get.canvasLayout.isEmpty)
  }

  test("python to state fingerprint is stable for positional calls") {
    val a = SnapTurtlePythonBridge.applyPython("forward(12345)", previousEmpty).toOption.get
    val stored = ProgrammingExercise.StateSerializer.serialize(a)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(stored.contains("12345"), clue = stored)
    assertEquals(
      SnapTurtlePythonBridge.topLevelCalls(restored.program.fullProgram).map(SnapTurtlePythonBridge.callName),
      List("forward")
    )
  }

  test("applyPython accepts full python-compatible palette program") {
    val result = SnapTurtlePythonBridge.applyPython(
      """receive_go()
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
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isRight, clue = result)
    val python = ProgrammingExerciseState.pythonOf(result.toOption.get)
    assert(python.contains("receive_go()"), clue = python)
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
  }
}
