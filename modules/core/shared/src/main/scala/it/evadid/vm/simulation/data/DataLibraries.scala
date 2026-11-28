package it.evadid.vm.simulation.data

import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType
import upickle.default.ReadWriter

/** One recorded operation on the provided number list; indices start at 0. Reads record the value they returned. */
enum ListCommand derives ReadWriter {
  @upickle.implicits.key("Get")
  case Get(index: Int, value: Int)
  @upickle.implicits.key("Set")
  case Set(index: Int, value: Int)
  @upickle.implicits.key("Swap")
  case Swap(first: Int, second: Int)
  @upickle.implicits.key("Length")
  case Length(length: Int)
}

/** One recorded operation on the provided sudoku; rows and columns start at 0, 0 marks an empty cell. */
enum SudokuCommand derives ReadWriter {
  @upickle.implicits.key("Get")
  case Get(row: Int, column: Int, value: Int)
  @upickle.implicits.key("Set")
  case Set(row: Int, column: Int, value: Int)
  @upickle.implicits.key("Remove")
  case Remove(row: Int, column: Int)
}

/** A 9×9 grid in row order. Givens (the nonzero cells of the puzzle) cannot be changed by a program. */
final case class SudokuGrid(cells: Vector[Int], givens: Set[Int]) derives ReadWriter {
  require(cells.size == 81 && cells.forall(value => value >= 0 && value <= 9), "A sudoku has 81 cells holding 0 to 9.")
  def apply(row: Int, column: Int): Int = cells(row * 9 + column)
  def updated(row: Int, column: Int, value: Int): SudokuGrid = copy(cells = cells.updated(row * 9 + column, value))
  def rows: Seq[Seq[Int]] = cells.grouped(9).toSeq
  def isComplete: Boolean = !cells.contains(0)

  /** Every row, column and 3×3 block contains each nonzero digit at most once. */
  def isConsistent: Boolean = {
    def unique(indices: Seq[Int]) = { val values = indices.map(cells).filter(_ != 0); values.distinct.size == values.size }
    (0 until 9).forall(i => unique((0 until 9).map(i * 9 + _)) && unique((0 until 9).map(_ * 9 + i)) &&
      unique(for r <- 0 until 3; c <- 0 until 3 yield ((i / 3) * 3 + r) * 9 + (i % 3) * 3 + c))
  }

  /** A complete, consistent grid that keeps all givens of `puzzle`. */
  def solves(puzzle: SudokuGrid): Boolean =
    isComplete && isConsistent && puzzle.givens.forall(index => cells(index) == puzzle.cells(index))
}

object SudokuGrid {
  /** Parses nine rows of digits; `0` or `.` marks an empty cell. Nonzero cells become givens. */
  def parse(rows: String*): SudokuGrid = {
    val digits = rows.mkString.filterNot(_.isWhitespace).map(ch => if ch == '.' then 0 else ch.asDigit).toVector
    SudokuGrid(digits, digits.indices.filter(digits(_) != 0).toSet)
  }

  val default: SudokuGrid = parse(
    "53.678912", "672195348", "198342567",
    "859761423", "426.53791", "713924856",
    "961537284", "287419635", "34528617.")
}

/** The data a program reads and changes through the list and sudoku libraries. */
final case class DataWorld(list: Vector[Int] = DataWorld.defaultList, sudoku: SudokuGrid = SudokuGrid.default) derives ReadWriter

object DataWorld {
  val defaultList: Vector[Int] = Vector(7, 3, 9, 1, 5)
}

/**
 * Function signatures of the `NumberList` and `Sudoku` modules ([[it.evadid.vm.modules.BeModules]]), bound
 * like `Turtle` but only through their class name. Java and Python print the calls as `NumberList.get(i)`.
 */
object DataLibraries {
  private def variable(name: String) = BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), BeDataType.Int)
  private def library(name: String, intrinsic: BeIntrinsic, signatures: List[(String, List[String], Boolean)]): BeDefineClass = {
    val definition = BeDefineClass.withMethods(BeEntityName.fromCodeString(name), Nil, signatures.map { (method, inputs, returnsValue) =>
      BeDefineClass.MethodSignature(BeEntityName.fromCodeString(method), inputs.map(variable),
        Some(BeDefineVariable(BeEntityName.fromUniversalNameInParts("result"), if returnsValue then BeDataType.Int else BeDataType.Unit)))
    })
    definition.copy(methods = definition.methods.map(method => method.copy(
      functionTypeInfo = method.functionTypeInfo.copy(funcType = BeDefineFunction.StaticMethod), intrinsic = Some(intrinsic))))
  }

  val numberList: BeDefineClass = library("NumberList", BeIntrinsic.NumberList, List(
    ("get", List("index"), true), ("set", List("index", "value"), false),
    ("swap", List("first", "second"), false), ("length", Nil, true)))

  val sudoku: BeDefineClass = library("Sudoku", BeIntrinsic.Sudoku, List(
    ("get", List("row", "column"), true), ("set", List("row", "column", "value"), false),
    ("remove", List("row", "column"), false)))

  private val byOwner = Map("numberlist" -> numberList, "number_list" -> numberList, "sudoku" -> sudoku)

  /** Resolves `NumberList.get`, `Sudoku.set`, …; unqualified names never bind to a library. */
  def resolveMethod(sourceName: String): Option[BeDefineFunction] = sourceName.trim.split('.').toList match {
    case owner :: method :: Nil => byOwner.get(owner.toLowerCase).flatMap(_.methods.find(
      _.functionTypeInfo.displayName.universalInterpretation() == BeEntityName.fromCodeString(method).universalInterpretation()))
    case _ => None
  }

  def methodName(definition: BeDefineFunction): String =
    definition.functionTypeInfo.displayName.universalInterpretation()
}
