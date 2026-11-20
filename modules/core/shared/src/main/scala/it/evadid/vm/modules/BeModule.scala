package it.evadid.vm.modules

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDependency, BeIntrinsic}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.simulation.data.{DataLibraries, ListCommand, SudokuCommand}
import upickle.default.ReadWriter

/** One call of a predefined module's function, recorded while a program runs, in program order. */
enum ModuleCommand derives ReadWriter {
  case Turtle(command: TurtleCommand[Double])
  case NumberList(command: ListCommand)
  case Sudoku(command: SudokuCommand)
}

/**
 * A predefined module: a library of static functions programs call as `<name>.<function>(…)` (the turtle
 * also unqualified in Python). Its functions carry `intrinsic`, so every frontend, printer and the
 * interpreter recognise them; `dependency` holds the definitions a printed program needs, where printing
 * adds them. Executing a program records each call as a typed command `C`.
 */
sealed abstract class BeModule[C](val name: String, val intrinsic: BeIntrinsic) {
  /** The module's functions as static methods of a class named `name`. */
  def library: BeDefineClass
  def dependency: Option[BeDependency]

  def owns(definition: BeDefineFunction): Boolean = definition.intrinsic.contains(intrinsic)

  /** The typed command of a recorded call, when it belongs to this module. */
  def command(recorded: ModuleCommand): Option[C]

  override def toString: String = name
}

object BeModules {
  object Turtle extends BeModule[TurtleCommand[Double]]("Turtle", BeIntrinsic.Turtle) {
    override def library: BeDefineClass = TurtleCommand.turtleClass
    override def dependency: Option[BeDependency] = Some(KnownDependencies.turtle)
    override def command(recorded: ModuleCommand): Option[TurtleCommand[Double]] =
      recorded match { case ModuleCommand.Turtle(command) => Some(command); case _ => None }
  }

  /** The number list sorting exercises work on. */
  object NumberList extends BeModule[ListCommand]("NumberList", BeIntrinsic.NumberList) {
    override def library: BeDefineClass = DataLibraries.numberList
    override def dependency: Option[BeDependency] = None
    override def command(recorded: ModuleCommand): Option[ListCommand] =
      recorded match { case ModuleCommand.NumberList(command) => Some(command); case _ => None }
  }

  object Sudoku extends BeModule[SudokuCommand]("Sudoku", BeIntrinsic.Sudoku) {
    override def library: BeDefineClass = DataLibraries.sudoku
    override def dependency: Option[BeDependency] = None
    override def command(recorded: ModuleCommand): Option[SudokuCommand] =
      recorded match { case ModuleCommand.Sudoku(command) => Some(command); case _ => None }
  }

  val all: Seq[BeModule[?]] = Seq(Turtle, NumberList, Sudoku)

  /** The module a function belongs to, if any. */
  def of(definition: BeDefineFunction): Option[BeModule[?]] = all.find(_.owns(definition))

  /** Modules whose calls are spelled with the module name in every language (all but the turtle). */
  def qualifiedName(definition: BeDefineFunction): Option[String] = of(definition).filter(_ ne Turtle).map(_.name)
}
