package it.evadid.vm.modules

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.simulation.data.{DataLibraries, DataWorld, ListCommand, SudokuCommand}
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

class BeModulesSpec extends FunSuite {
  private val mixed = ProgrammingStateRaw(Python,
    "forward(10)\nNumberList.swap(0, 1)\nSudoku.set(0, 2, 4)\nright(90)\nNumberList.length()\n").toBeExpression

  test("the registry lists the predefined modules, each owning exactly its library's functions") {
    assertEquals(BeModules.all.map(_.name), Seq("Turtle", "NumberList", "Sudoku", "SystemFunctionality"))
    BeModules.all.foreach { module =>
      assert(module.library.methods.nonEmpty, module.name)
      assert(module.library.methods.forall(module.owns), module.name)
      assert(module.library.methods.forall(method => BeModules.of(method).contains(module)), module.name)
      assertEquals(BeModules.all.count(other => module.library.methods.exists(other.owns)), 1, module.name)
    }
    assertEquals(BeModules.NumberList.library, DataLibraries.numberList)
    assertEquals(BeModules.Turtle.library, TurtleCommand.turtleClass)
    assert(BeModules.Turtle.dependency.nonEmpty && BeModules.NumberList.dependency.isEmpty)
  }

  test("data modules are spelled with their name in every language; the turtle keeps its own spellings") {
    assertEquals(BeModules.qualifiedName(DataLibraries.numberList.methods.head), Some("NumberList"))
    assertEquals(BeModules.qualifiedName(DataLibraries.sudoku.methods.head), Some("Sudoku"))
    assertEquals(BeModules.qualifiedName(TurtleCommand.turtleClass.methods.head), None)
    assertEquals(BeModules.of(TurtleCommand.turtleClass.methods.head), Some(BeModules.Turtle))
  }

  test("an expression lists the calls of one module's functions in source order") {
    val expression = mixed.expression
    assertEquals(expression.moduleCalls(BeModules.Turtle).map(_.funcDef.functionTypeInfo.displayName.universalInterpretation()),
      Vector("forward", "right"))
    assertEquals(expression.moduleCalls(BeModules.NumberList).map(DataLibraries.methodName.compose(_.funcDef)), Vector("swap", "length"))
    assertEquals(expression.moduleCalls(BeModules.Sudoku).size, 1)
    val java = ProgrammingStateRaw(Java, "public class Main {\n  static void f() { NumberList.get(0); }\n  public static void main(String[] args) { f(); }\n}")
    assertEquals(java.toBeExpression.expression.moduleCalls(BeModules.NumberList).size, 1, "calls inside functions count too")
    assertEquals(java.toBeExpression.expression.moduleCalls(BeModules.Turtle), Vector.empty)
  }

  test("a run yields each module's executed calls as that module's typed commands, keeping their order") {
    val run = mixed.execute(DataWorld(list = Vector(2, 1)))
    assertEquals(run.status, TurtleExecutionStatus.Completed)
    assertEquals(run.moduleCommands(BeModules.Turtle).map(_.name.universalInterpretation()), List("forward", "right"))
    assertEquals(run.moduleCommands(BeModules.NumberList), List(ListCommand.Swap(0, 1), ListCommand.Length(2)))
    assertEquals(run.moduleCommands(BeModules.Sudoku), List(SudokuCommand.Set(0, 2, 4)))
    assertEquals(run.recorded, List(
      ModuleCommand.Turtle(TurtleCommand.Forward(10.0)), ModuleCommand.NumberList(ListCommand.Swap(0, 1)),
      ModuleCommand.Sudoku(SudokuCommand.Set(0, 2, 4)), ModuleCommand.Turtle(TurtleCommand.Right(90.0)),
      ModuleCommand.NumberList(ListCommand.Length(2))))
    assertEquals(mixed.moduleCommands(BeModules.NumberList), mixed.execute().moduleCommands(BeModules.NumberList), "the state runs startup on the default data")
    assertEquals(mixed.moduleCommands(BeModules.NumberList).last, ListCommand.Length(DataWorld.defaultList.size))
  }

  test("completed module commands refuse partial runs") {
    val failing = ProgrammingStateRaw(Python, "NumberList.swap(0, 1)\nNumberList.get(99)\n").toBeExpression.execute()
    assertEquals(failing.moduleCommands(BeModules.NumberList), List(ListCommand.Swap(0, 1)), "commands before the failure are kept")
    intercept[IllegalArgumentException](failing.completedModuleCommands(BeModules.NumberList))
  }
}
