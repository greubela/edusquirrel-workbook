package it.evadid.vm.simulation.data

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.defining.BeIntrinsic
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.simulation.{ProgramEntry, ProgramExecution}
import it.evadid.vm.simulation.turtle.TurtleProgramAnalysis
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import it.evadid.vm.modules.{BeModules, ModuleCommand}
import munit.FunSuite
import upickle.default.*

class DataLibrariesSpec extends FunSuite {
  private val solved = SudokuGrid.parse("534678912", "672195348", "198342567", "859761423", "426853791",
    "713924856", "961537284", "287419635", "345286179")

  test("library methods resolve only when qualified, for both spellings of the owner") {
    assertEquals(DataLibraries.resolveMethod("NumberList.get").flatMap(_.intrinsic), Some(BeIntrinsic.NumberList))
    assertEquals(DataLibraries.resolveMethod("number_list.length").flatMap(_.intrinsic), Some(BeIntrinsic.NumberList))
    assertEquals(DataLibraries.resolveMethod("Sudoku.remove").flatMap(_.intrinsic), Some(BeIntrinsic.Sudoku))
    assertEquals(DataLibraries.resolveMethod("get"), None)
    assertEquals(DataLibraries.resolveMethod("NumberList.sort"), None)
    assertEquals(DataLibraries.resolveMethod("Other.get"), None)
    assertEquals(DataLibraries.numberList.methods.map(DataLibraries.methodName).toSet, Set("get", "set", "swap", "length"))
    assertEquals(DataLibraries.sudoku.methods.map(DataLibraries.methodName).toSet, Set("get", "set", "remove"))
    val get = DataLibraries.resolveMethod("Sudoku.get").get
    assertEquals((BeModules.of(get), BeModules.qualifiedName(get)), (Some(BeModules.Sudoku), Some("Sudoku")))
    assertEquals(get.inputs.size, 2)
  }

  private def calls(language: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage, code: String): List[BeFunctionCall] =
    ProgrammingStateRaw(language, code).toBeExpression.expression.subtree.collect { case call: BeFunctionCall => call }.toList

  test("Python and Java bind library calls; unqualified user functions with library method names stay ordinary") {
    assert(calls(Python, "NumberList.swap(0, 1)\n").exists(call => BeModules.qualifiedName(call.funcDef).nonEmpty))
    assert(calls(Java, "public class Main {\n  public static void main(String[] args) {\n    Sudoku.set(0, 2, 4);\n  }\n}").exists(call =>
      call.funcDef.intrinsic.contains(BeIntrinsic.Sudoku)))
    assert(!calls(Python, "def get(i):\n    return i\nget(1)\n").exists(call => BeModules.qualifiedName(call.funcDef).nonEmpty))
    val shadowed = calls(Python, "def swap(a, b):\n    pass\nswap(0, 1)\n")
    assert(shadowed.nonEmpty && !shadowed.exists(call => BeModules.qualifiedName(call.funcDef).nonEmpty))
  }

  test("library calls are not counted as turtle command sites") {
    val expression = ProgrammingStateRaw(Python, "NumberList.swap(0, 1)\nforward(10)\n").toBeExpression.expression
    assertEquals(TurtleProgramAnalysis.commandSites(expression).map(_.name.universalInterpretation()), List("forward"))
  }

  test("sudoku grids parse dots as empty cells and keep the given cells") {
    val grid = SudokuGrid.parse("5.3......", ".........", ".........", ".........", ".........", ".........", ".........", ".........", "........9")
    assertEquals((grid(0, 0), grid(0, 1), grid(0, 2), grid(8, 8)), (5, 0, 3, 9))
    assertEquals(grid.givens, Set(0, 2, 80))
    assertEquals(grid.updated(0, 1, 7)(0, 1), 7)
    intercept[IllegalArgumentException](SudokuGrid(Vector.fill(80)(0), Set.empty))
    intercept[IllegalArgumentException](SudokuGrid(Vector.fill(81)(10), Set.empty))
  }

  test("sudoku consistency detects repeats in rows, columns and blocks separately") {
    val empty = SudokuGrid(Vector.fill(81)(0), Set.empty)
    assert(empty.isConsistent && !empty.isComplete)
    assert(!empty.updated(0, 0, 1).updated(0, 8, 1).isConsistent, "row")
    assert(!empty.updated(0, 0, 1).updated(8, 0, 1).isConsistent, "column")
    assert(!empty.updated(0, 0, 1).updated(2, 2, 1).isConsistent, "block")
    assert(empty.updated(0, 0, 1).updated(4, 4, 1).isConsistent, "different row, column and block")
  }

  test("a solution must be complete, consistent and keep the puzzle's givens") {
    assert(solved.solves(SudokuGrid.default))
    assert(!SudokuGrid.default.solves(SudokuGrid.default), "incomplete")
    val otherGiven = SudokuGrid(SudokuGrid.default.cells.updated(1, 9), SudokuGrid.default.givens)
    assert(!solved.solves(otherGiven), "a given differs from the solution")
  }
}
