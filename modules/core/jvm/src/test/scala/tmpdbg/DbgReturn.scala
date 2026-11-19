package tmpdbg
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
class DbgReturn extends munit.FunSuite {
  test("dbg") {
    for src <- List("def big(n: float) -> bool:\n    return n > 10\n\nif big(20):\n    forward(1)\nelse:\n    left(90)\n",
      "def double(x: float) -> float:\n    return x * 2\n\nforward(double(3))\n",
      "def double(x: float) -> float:\n    return x * 2\n\nif double(3) > 5:\n    forward(1)\n",
      "for i in range(3):\n    if i * 4 > 10:\n        forward(i)\n    else:\n        left(90)\n") do
      val info = ProgrammingStateRaw(Python, src).toBeExpression.turtleCommandExecutionInfo
      println("DBG " + src.replace("\n", "; ") + " => " + info.executionStatus + " " + info.executedTurtleCommandsInOrder)
  }
}
