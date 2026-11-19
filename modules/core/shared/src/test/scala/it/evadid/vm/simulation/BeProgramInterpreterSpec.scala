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

  private val arithmetic = java(
    """    static int rechne(int a, int b) {
      |        return a % b + Math.floor(a / b) * 10 - (-b);
      |    }
      |    static int zaehle(int n) {
      |        int k = 0;
      |        while (k < n) {
      |            k = k + 1;
      |            NumberList.set(0, k);
      |        }
      |        return k;
      |    }
      |    static void teile(int a) {
      |        NumberList.set(0, 10 / a);
      |    }
      |    public static void main(String[] args) {
      |    }""".stripMargin).toBeExpression

  test("operators follow Python semantics for remainder and floor division; while loops repeat until false") {
    assertEquals(arithmetic.execute(entry = ProgramEntry.Call("rechne", Seq(7, 2))).returnValue, Some(1.0 + 30.0 + 2.0))
    assertEquals(arithmetic.execute(entry = ProgramEntry.Call("rechne", Seq(-7, 2))).returnValue, Some(1.0 - 40.0 + 2.0))
    val counted = arithmetic.execute(DataWorld(list = Vector(0)), ProgramEntry.Call("zaehle", Seq(3)))
    assertEquals((counted.returnValue, counted.world.list), (Some(3.0), Vector(3)))
  }

  test("invalid calls and values fail with an explanation instead of an exception") {
    def status(entry: ProgramEntry, world: DataWorld = DataWorld()) = arithmetic.execute(world, entry).status.toString
    assert(status(ProgramEntry.Call("rechne", Seq(1))).contains("expects 2 values"))
    assert(status(ProgramEntry.Call("teile", Seq(0))).contains("Division by zero"))
    assert(status(ProgramEntry.Call("teile", Seq(4))).contains("whole number"), "2.5 cannot be stored in the list")
    val sudoku = java(
      """    static void falsch() {
        |        Sudoku.set(0, 2, 10);
        |    }
        |    static void ausserhalb() {
        |        Sudoku.get(9, 0);
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    assert(sudoku.execute(entry = ProgramEntry.Call("falsch")).status.toString.contains("1 to 9"))
    assert(sudoku.execute(entry = ProgramEntry.Call("ausserhalb")).status.toString.contains("outside the sudoku"))
  }

  test("step, call-depth and data-command limits end execution and keep the recorded commands") {
    val recursion = java(
      """    static void tief(int n) {
        |        NumberList.length();
        |        tief(n + 1);
        |    }
        |    public static void main(String[] args) {
        |    }""".stripMargin).toBeExpression
    val deep = recursion.execute(entry = ProgramEntry.Call("tief", Seq(0)), limits = TurtleExecutionLimits(maxCallDepth = 5))
    assertEquals((deep.status, deep.listCommands.size), (TurtleExecutionStatus.LimitExceeded, 5))
    val bounded = arithmetic.execute(DataWorld(list = Vector(0)), ProgramEntry.Call("zaehle", Seq(100)), TurtleExecutionLimits(maxDataCommands = 4))
    assertEquals((bounded.status, bounded.listCommands.size), (TurtleExecutionStatus.LimitExceeded, 4))
    val steps = arithmetic.execute(DataWorld(list = Vector(0)), ProgramEntry.Call("zaehle", Seq(100)), TurtleExecutionLimits(maxSteps = 20))
    assertEquals(steps.status, TurtleExecutionStatus.LimitExceeded)
    intercept[IllegalArgumentException](steps.completedListCommands)
  }

  test("cancellation stops a run, and startup runs record turtle and list commands together") {
    val cancelled = BeProgramInterpreter.run(swap.toBeExpression.expression, isCancelled = () => true)
    assertEquals(cancelled.status, TurtleExecutionStatus.Cancelled)
    val mixed = ProgrammingStateRaw(Python, "forward(10)\nNumberList.swap(0, 1)\nright(90)\n").toBeExpression.programExecution
    assertEquals(mixed.completedTurtleCommands.map(_.name.universalInterpretation()), List("forward", "right"))
    assertEquals(mixed.completedListCommands, List(ListCommand.Swap(0, 1)))
  }

  test("recursive calls keep their caller's locals, while functions still update module variables") {
    val program = ProgrammingStateRaw(Python,
      """zaehler = 0
        |def zaehle():
        |    global zaehler
        |    zaehler = zaehler + 1
        |def tiefe(n):
        |    for i in range(3):
        |        if n > 0:
        |            tiefe(n - 1)
        |        NumberList.set(0, i)
        |    return n
        |zaehle()
        |zaehle()
        |NumberList.set(1, zaehler)
        |""".stripMargin).toBeExpression
    val startup = program.execute(DataWorld(list = Vector(0, 0)))
    assertEquals(startup.world.list, Vector(0, 2), startup.status.toString)
    val nested = program.execute(DataWorld(list = Vector(0, 0)), ProgramEntry.Call("tiefe", Seq(2)))
    // Every level runs its own loop to the end: 3 + 3·3 + 3·3·3 assignments, the last one from the outermost loop.
    assertEquals((nested.status, nested.returnValue), (TurtleExecutionStatus.Completed, Some(2.0)))
    assertEquals(nested.listCommands.size, 39)
    assertEquals(nested.world.list, Vector(2, 0))
  }
}
