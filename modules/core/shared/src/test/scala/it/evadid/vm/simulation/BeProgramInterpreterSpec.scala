package it.evadid.vm.simulation

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.simulation.data.{DataWorld, ListCommand, SudokuCommand, SudokuGrid}
import it.evadid.vm.simulation.turtle.{TurtleExecutionLimits, TurtleExecutionStatus}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStateRaw}
import munit.FunSuite

class BeProgramInterpreterSpec extends FunSuite {
  private def java(body: String) = ProgrammingStateJavaStructured(s"public class Main {\n$body\n}")

  private val swap = java(
    """    static void tausche(int i, int j) {
      |        int merke = NumberList.get(i);
      |        NumberList.set(i, NumberList.get(j));
      |        NumberList.set(j, merke);
      |    }
      |    public static void main(String[] args) {
      |        tausche(0, 2);
      |    }""".stripMargin)

  test("list commands are extracted from a call like turtle commands, with the data afterwards") {
    val run = swap.toBeExpression.execute(DataWorld(list = Vector(4, 8, 15)), ProgramEntry.Call("tausche", Seq(0, 2)))
    assertEquals(run.status, TurtleExecutionStatus.Completed)
    assertEquals(run.completedListCommands, List(ListCommand.Get(0, 4), ListCommand.Get(2, 15), ListCommand.Set(0, 15), ListCommand.Set(2, 4)))
    assertEquals(run.world.list, Vector(15, 8, 4))
    assertEquals(run.turtleCommands, Nil)
  }

  test("the startup run uses the default data and is available on the expression state") {
    val run = swap.toBeExpression.programExecution
    assertEquals(run.world.list, Vector(9, 3, 7, 1, 5))
    assertEquals(run.listCommands.count(_.isInstanceOf[ListCommand.Set]), 2)
  }

  test("library calls print qualified and bind again after a round trip through Python") {
    val python = swap.toPython.code
    assert(python.contains("NumberList.get(i)"), python)
    assert(swap.toJava.code.contains("NumberList.set(j, merke)"))
    val again = ProgrammingStateRaw(Python, python).toBeExpression.execute(DataWorld(list = Vector(1, 2, 3)), ProgramEntry.Call("tausche", Seq(0, 2)))
    assertEquals(again.world.list, Vector(3, 2, 1))
  }

  test("functions return values, recurse and short-circuit conditions") {
    val program = java(
      """    static int fakultaet(int n) {
        |        if (n <= 1) {
        |            return 1;
        |        }
        |        return n * fakultaet(n - 1);
        |    }
        |    static boolean sicher(int position) {
        |        return position < NumberList.length() && NumberList.get(position) > 0;
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    assertEquals(program.execute(entry = ProgramEntry.Call("fakultaet", Seq(5))).returnValue, Some(120.0))
    val guarded = program.execute(DataWorld(list = Vector(3)), ProgramEntry.Call("sicher", Seq(4)))
    assertEquals((guarded.status, guarded.returnValue), (TurtleExecutionStatus.Completed, Some(0.0)))
    assertEquals(guarded.listCommands, List(ListCommand.Length(1)), "the right operand is not evaluated")
  }

  test("for loops ascend and run zero times when the start is beyond the end, as in Java and Python") {
    val program = java(
      """    static void markiere() {
        |        for (int i = 0; i < NumberList.length() - 1; i++) {
        |            NumberList.set(i, 0);
        |        }
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    val single = program.execute(DataWorld(list = Vector(7)), ProgramEntry.Call("markiere"))
    assertEquals((single.status, single.world.list, single.listCommands), (TurtleExecutionStatus.Completed, Vector(7), List(ListCommand.Length(1))))
  }

  test("sudoku operations record commands, read 0 for empty cells and protect givens") {
    val program = java(
      """    static void probe() {
        |        Sudoku.set(0, 2, Sudoku.get(0, 2) + 4);
        |        Sudoku.remove(0, 2);
        |        Sudoku.set(0, 0, 1);
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    val run = program.execute(entry = ProgramEntry.Call("probe"))
    assertEquals(run.sudokuCommands, List(SudokuCommand.Get(0, 2, 0), SudokuCommand.Set(0, 2, 4), SudokuCommand.Remove(0, 2)))
    assert(run.status.toString.contains("given"), run.status.toString)
    intercept[IllegalArgumentException](run.completedSudokuCommands)
    assertEquals(run.world.sudoku, SudokuGrid.default)
  }

  test("runtime problems and endless loops end with an explanatory status instead of an exception") {
    val program = java(
      """    static void weit() {
        |        NumberList.get(NumberList.length());
        |    }
        |    static void endlos() {
        |        while (true) {
        |            NumberList.length();
        |        }
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    assert(program.execute(entry = ProgramEntry.Call("weit")).status.toString.contains("outside the list"))
    assertEquals(program.execute(entry = ProgramEntry.Call("endlos"), limits = TurtleExecutionLimits(maxLoopIterations = 50)).status,
      TurtleExecutionStatus.LimitExceeded)
    assert(program.execute(entry = ProgramEntry.Call("fehlt")).status.toString.contains("does not define fehlt"))
  }

  test("sudoku grids check completeness, consistency and givens") {
    val solved = SudokuGrid.parse("534678912", "672195348", "198342567", "859761423", "426853791",
      "713924856", "961537284", "287419635", "345286179")
    assert(solved.solves(SudokuGrid.default))
    assert(!solved.updated(0, 0, 3).isConsistent)
    assert(!SudokuGrid.default.isComplete)
  }
}
