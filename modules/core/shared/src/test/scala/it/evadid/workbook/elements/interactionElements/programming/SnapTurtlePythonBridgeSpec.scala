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
      """i = 1
        |while i < 3:
        |    i = i + 1
        |forward(10)
        |""".stripMargin,
      previousEmpty
    )
    assert(result.isLeft, clue = result)
    assert(result.swap.toOption.get.toLowerCase.contains("unsupported"), clue = result)
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
}
