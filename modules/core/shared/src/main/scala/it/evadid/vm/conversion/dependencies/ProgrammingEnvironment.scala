package it.evadid.vm.conversion.dependencies

import it.evadid.vm.code.defining.{BeDefineFunction, BeIntrinsic}
import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.vm.conversion.parsed.SymbolTable.KnownStructure
import upickle.default.ReadWriter

/** A library programs can call without defining it, named stably for exercise content. */
enum ProgrammingLibrary(val intrinsics: Set[BeIntrinsic]) derives ReadWriter {
  /** Turtle graphics (`forward`, `Turtle.forward`). */
  @upickle.implicits.key("Turtle")
  case Turtle extends ProgrammingLibrary(Set(BeIntrinsic.Turtle))
  /** Console output (`print`, `System.out.println`, `System.err.println`). */
  @upickle.implicits.key("System")
  case System extends ProgrammingLibrary(Set(BeIntrinsic.Print, BeIntrinsic.PrintErr))
  /** Reading the text behind a URL (`fetch_text`, `Web.fetchText`). */
  @upickle.implicits.key("Web")
  case Web extends ProgrammingLibrary(Set(BeIntrinsic.FetchText))
  /** The number list of sorting exercises. */
  @upickle.implicits.key("NumberList")
  case NumberList extends ProgrammingLibrary(Set(BeIntrinsic.NumberList))
  /** The sudoku grid of data exercises. */
  @upickle.implicits.key("Sudoku")
  case Sudoku extends ProgrammingLibrary(Set(BeIntrinsic.Sudoku))
}

/** The libraries an exercise provides by default. A call into any other library binds like a call of undefined
  * code, so it becomes part of the program's [[it.evadid.vm.code.defining.BeUnknownDependency]]. Language
  * built-ins (operators, `int`, `max`, …) always exist.
  */
final case class ProgrammingEnvironment(libraries: Set[ProgrammingLibrary]) derives ReadWriter {
  def provides(intrinsic: BeIntrinsic): Boolean =
    libraries.exists(_.intrinsics(intrinsic)) || !ProgrammingLibrary.values.exists(_.intrinsics(intrinsic))

  def provides(function: BeDefineFunction): Boolean = function.intrinsic.forall(provides)

  /** The parser's predefined names in this environment. */
  def knownStructures: Seq[KnownStructure] = SymbolTable.defaultKnownStructures.filter {
    case KnownStructure.Function(_, function) => provides(function)
    case KnownStructure.Operator(_, function) => provides(function)
    case _ => true
  }
}

object ProgrammingEnvironment {
  /** Every library: what an exercise without a configured environment provides. */
  val all: ProgrammingEnvironment = ProgrammingEnvironment(ProgrammingLibrary.values.toSet)

  def of(libraries: ProgrammingLibrary*): ProgrammingEnvironment = ProgrammingEnvironment(libraries.toSet)
}
