package it.evadid.vm.simulation

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.simulation.data.{DataWorld, ListCommand, SudokuCommand, SudokuGrid}
import it.evadid.vm.simulation.turtle.{TurtleExecutionLimits, TurtleExecutionStatus}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStateRaw}
import it.evadid.vm.modules.BeModules
import munit.FunSuite

class BeProgramInterpreterSpec extends FunSuite {
  private def java(body: String) = ProgrammingStateJavaStructured(s"public class Main {\n$body\n}")
  /** Helper methods called directly, with an empty startup. */
  private def withEmptyMain(methods: String) =
    java(methods + "\n    public static void main(String[] args) {\n    }").toBeExpression

  test("println preserves empty lines and escaped newlines through Python and never becomes a turtle command") {
    val program = java("""static void main(String[] args) {
      | System.out.println("first\nsecond");
      | System.out.println();
      | System.out.println("last");
      | Turtle.forward(20);
      |}""".stripMargin)
    val execution = program.toBeExpression.programExecution
    assert(execution.completed, execution.status.toString)
    assertEquals(execution.stdout, "first\nsecond\n\nlast\n")
    assertEquals(execution.moduleCommands(BeModules.Turtle).size, 1)
    val python = program.toPython
    assert(python.code.contains("print("), python.code)
    val converted = python.toBeExpression.programExecution
    assertEquals(converted.stdout, execution.stdout)
    assertEquals(converted.moduleCommands(BeModules.Turtle), execution.moduleCommands(BeModules.Turtle))
  }

  test("system functions bind to a static module and preserve stdout/stderr separately") {
    val source = java("""static void main(String[] args) {
      | System.out.println("out");
      | System.err.println("error");
      | System.err.println();
      |}""".stripMargin)
    val expression = source.toBeExpression
    val calls = expression.expression.moduleCalls(BeModules.System)
    assertEquals(calls.map(_.funcDef.functionTypeInfo.displayName.universalInterpretation()).toList,
      List("printToOut", "printToErr", "printToErr"))
    assert(calls.forall(_.target.isInstanceOf[it.evadid.vm.code.usage.BeCallTarget.StaticMethod]))
    assertEquals(expression.programExecution.stdout, "out\n")
    assertEquals(expression.programExecution.stderr, "error\n\n")
    assertEquals(expression.turtleCommandExecutionInfo.completedCommands, Nil)
    val python = source.toPython.code
    assert(python.startsWith("import sys\n"), python)
    assert(python.contains("print(\"error\", file=sys.stderr)") && python.contains("print(file=sys.stderr)"), python)
    val back = source.toPython.toBeExpression.programExecution
    assertEquals(back.stdout, expression.programExecution.stdout)
    assertEquals(back.stderr, expression.programExecution.stderr)
  }

  test("Python keyword arguments: print(..., file=sys.stderr) is the error stream, other keywords stay placeholders") {
    val written = ProgrammingStateRaw(Python, "import sys\nprint(\"a\", 1, file=sys.stderr)\nprint(\"b\")\nprint(\"c\", end=\"\")\n")
    val expression = written.toBeExpression
    assertEquals(expression.programExecution.stderr, "a 1\n")
    assertEquals(expression.programExecution.stdout, "b\n")
    val unsupported = expression.expression.subtree.collect { case value: it.evadid.vm.code.errors.BeExpressionUnsupported => value }
    assertEquals(unsupported.size, 1, expression.expression.toString)
    val java = ProgrammingStateRaw(Python, "import sys\nprint(\"a\", file=sys.stderr)\n").toJava.code
    assert(java.contains("System.err.println(\"a\");"), java)
    intercept[IllegalArgumentException](written.toJava)
  }

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
    assertEquals(run.completedModuleCommands(BeModules.NumberList), List(ListCommand.Get(0, 4), ListCommand.Get(2, 15), ListCommand.Set(0, 15), ListCommand.Set(2, 4)))
    assertEquals(run.world.list, Vector(15, 8, 4))
    assertEquals(run.moduleCommands(BeModules.Turtle), Nil)
  }

  test("the startup run uses the default data and is available on the expression state") {
    val run = swap.toBeExpression.programExecution
    assertEquals(run.world.list, Vector(9, 3, 7, 1, 5))
    assertEquals(run.moduleCommands(BeModules.NumberList).count(_.isInstanceOf[ListCommand.Set]), 2)
  }

  test("library calls print qualified and bind again after a round trip through Python") {
    val python = swap.toPython.code
    assert(python.contains("NumberList.get(i)"), python)
    assert(swap.toJava.code.contains("NumberList.set(j, merke)"))
    val again = ProgrammingStateRaw(Python, python).toBeExpression.execute(DataWorld(list = Vector(1, 2, 3)), ProgramEntry.Call("tausche", Seq(0, 2)))
    assertEquals(again.world.list, Vector(3, 2, 1))
  }

  test("functions return values, recurse and short-circuit conditions") {
    val program = withEmptyMain(
      """    static int fakultaet(int n) {
        |        if (n <= 1) {
        |            return 1;
        |        }
        |        return n * fakultaet(n - 1);
        |    }
        |    static boolean sicher(int position) {
        |        return position < NumberList.length() && NumberList.get(position) > 0;
        |    }""".stripMargin)
    assertEquals(program.execute(entry = ProgramEntry.Call("fakultaet", Seq(5))).returnValue, Some(120.0))
    val guarded = program.execute(DataWorld(list = Vector(3)), ProgramEntry.Call("sicher", Seq(4)))
    assertEquals((guarded.status, guarded.returnValue), (TurtleExecutionStatus.Completed, Some(0.0)))
    assertEquals(guarded.moduleCommands(BeModules.NumberList), List(ListCommand.Length(1)), "the right operand is not evaluated")
  }

  test("for loops ascend and run zero times when the start is beyond the end, as in Java and Python") {
    val program = withEmptyMain(
      """    static void markiere() {
        |        for (int i = 0; i < NumberList.length() - 1; i++) {
        |            NumberList.set(i, 0);
        |        }
        |    }""".stripMargin)
    val single = program.execute(DataWorld(list = Vector(7)), ProgramEntry.Call("markiere"))
    assertEquals((single.status, single.world.list, single.moduleCommands(BeModules.NumberList)), (TurtleExecutionStatus.Completed, Vector(7), List(ListCommand.Length(1))))
  }

  test("sudoku operations record commands, read 0 for empty cells and protect givens") {
    val program = withEmptyMain(
      """    static void probe() {
        |        Sudoku.set(0, 2, Sudoku.get(0, 2) + 4);
        |        Sudoku.remove(0, 2);
        |        Sudoku.set(0, 0, 1);
        |    }""".stripMargin)
    val run = program.execute(entry = ProgramEntry.Call("probe"))
    assertEquals(run.moduleCommands(BeModules.Sudoku), List(SudokuCommand.Get(0, 2, 0), SudokuCommand.Set(0, 2, 4), SudokuCommand.Remove(0, 2)))
    assert(run.status.toString.contains("given"), run.status.toString)
    intercept[IllegalArgumentException](run.completedModuleCommands(BeModules.Sudoku))
    assertEquals(run.world.sudoku, SudokuGrid.default)
  }

  test("runtime problems and endless loops end with an explanatory status instead of an exception") {
    val program = withEmptyMain(
      """    static void weit() {
        |        NumberList.get(NumberList.length());
        |    }
        |    static void endlos() {
        |        while (true) {
        |            NumberList.length();
        |        }
        |    }""".stripMargin)
    assert(program.execute(entry = ProgramEntry.Call("weit")).status.toString.contains("outside the list"))
    assertEquals(program.execute(entry = ProgramEntry.Call("endlos"), limits = TurtleExecutionLimits(maxLoopIterations = 50)).status,
      TurtleExecutionStatus.LimitExceeded)
    assert(program.execute(entry = ProgramEntry.Call("fehlt")).status.toString.contains("does not define fehlt"))
  }

  private val arithmetic = withEmptyMain(
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
      |    }""".stripMargin)

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
    val sudoku = withEmptyMain(
      """    static void falsch() {
        |        Sudoku.set(0, 2, 10);
        |    }
        |    static void ausserhalb() {
        |        Sudoku.get(9, 0);
        |    }""".stripMargin)
    assert(sudoku.execute(entry = ProgramEntry.Call("falsch")).status.toString.contains("1 to 9"))
    assert(sudoku.execute(entry = ProgramEntry.Call("ausserhalb")).status.toString.contains("outside the sudoku"))
  }

  test("step, call-depth and data-command limits end execution and keep the recorded commands") {
    val recursion = withEmptyMain(
      """    static void tief(int n) {
        |        NumberList.length();
        |        tief(n + 1);
        |    }""".stripMargin)
    val deep = recursion.execute(entry = ProgramEntry.Call("tief", Seq(0)), limits = TurtleExecutionLimits(maxCallDepth = 5))
    assertEquals((deep.status, deep.moduleCommands(BeModules.NumberList).size), (TurtleExecutionStatus.LimitExceeded, 5))
    val bounded = arithmetic.execute(DataWorld(list = Vector(0)), ProgramEntry.Call("zaehle", Seq(100)), TurtleExecutionLimits(maxDataCommands = 4))
    assertEquals((bounded.status, bounded.moduleCommands(BeModules.NumberList).size), (TurtleExecutionStatus.LimitExceeded, 4))
    val steps = arithmetic.execute(DataWorld(list = Vector(0)), ProgramEntry.Call("zaehle", Seq(100)), TurtleExecutionLimits(maxSteps = 20))
    assertEquals(steps.status, TurtleExecutionStatus.LimitExceeded)
    intercept[IllegalArgumentException](steps.completedModuleCommands(BeModules.NumberList))
  }

  test("cancellation stops a run, and startup runs record turtle and list commands together") {
    val cancelled = BeProgramInterpreter.run(swap.toBeExpression.expression, isCancelled = () => true)
    assertEquals(cancelled.status, TurtleExecutionStatus.Cancelled)
    val mixed = ProgrammingStateRaw(Python, "forward(10)\nNumberList.swap(0, 1)\nright(90)\n").toBeExpression.programExecution
    assertEquals(mixed.completedModuleCommands(BeModules.Turtle).map(_.name.universalInterpretation()), List("forward", "right"))
    assertEquals(mixed.completedModuleCommands(BeModules.NumberList), List(ListCommand.Swap(0, 1)))
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
    assertEquals(nested.moduleCommands(BeModules.NumberList).size, 39)
    assertEquals(nested.world.list, Vector(2, 0))
  }
}
