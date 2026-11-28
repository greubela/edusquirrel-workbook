package it.evadid.workbook.interactions.programming.programmingExerciseRegular

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId, WorkbookContent}
import it.evadid.vm.modules.BeModules
import it.evadid.vm.simulation.data.{DataWorld, ListCommand}
import it.evadid.vm.simulation.turtle.{TurtleExecutionLimits, TurtleExecutionStatus}
import it.evadid.vm.simulation.{ProgramEntry, ProgramExecution}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.interactions.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.abstractions.ExecutionContextInformation
import upickle.default.ReadWriter

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.control.NonFatal

/** Operations of the number list, for expectations about which ones a method uses. */
enum ListOperation derives ReadWriter {
  case Get, Set, Swap, Length
}

object ListOperation {
  def of(command: ListCommand): ListOperation = command match {
    case _: ListCommand.Get => Get
    case _: ListCommand.Set => Set
    case _: ListCommand.Swap => Swap
    case _: ListCommand.Length => Length
  }
}

/** One checked property of a finished call: the data afterwards, the return value or the recorded commands. */
enum DataExpectation derives ReadWriter {
  case ListEquals(values: Seq[Int])
  case SudokuSolved
  case SudokuUnchanged
  case Returns(value: Boolean)
  case UsesListOperation(operation: ListOperation)
  case AvoidsListOperation(operation: ListOperation)
  /** Every swap exchanges neighbouring positions, as in bubble sort. */
  case OnlyAdjacentSwaps
  case SwapCount(count: Int)
}

/**
 * A unit test evaluated in the browser on the shared interpreter: calls `function` with `arguments`
 * on `world` and checks `expectations`. Failures explain what differed; hidden tests stay silent.
 */
case class ProgramDataTestGrader(
                                  testId: String,
                                  publicDescription: LanguageMapContentId,
                                  function: String,
                                  arguments: Seq[Double],
                                  world: DataWorld,
                                  expectations: Seq[DataExpectation],
                                  importance: GradingImportance = GradingImportance.REQUIRED_TEST,
                                  limits: TurtleExecutionLimits = ProgramDataTestGrader.defaultLimits,

                                  /** Values for `{name}` placeholders in the description, e.g. the list a generated test sorts. */
                                  publicDescriptionArguments: Map[String, String] = Map.empty,
    /** The libraries the exercise provides; its exercise sets it ([[ProgrammingTestGrader.inEnvironment]]). */
    environment: ProgrammingEnvironment = ProgrammingEnvironment.all
                                ) extends ProgrammingTestGrader derives ReadWriter {
  override def inEnvironment(environment: ProgrammingEnvironment): ProgramDataTestGrader = copy(environment = environment)
  require(testId.nonEmpty && testId == testId.trim, "A data test needs a nonempty ID.")
  require(expectations.nonEmpty, "A data test needs at least one expectation.")
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity

  override protected def handleGrading(state: ProgrammingState, context: ExecutionContextInformation)
  : Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = Future {
    val (status, message) = evaluate(state)
    val res: GradingResult[ProgrammingState, AdditionalProgrammingTestInfo] = if (message.nonEmpty && importance != GradingImportance.HIDDEN_TEST) {
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status, message.map(WorkbookContent(_)), None, LocalDateTime.now())
    } else {
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status, None, None, LocalDateTime.now())
    }

    res
  }(using context.futureExecutionContext)

  /** Status plus an optional explanation; exposed for authoring checks. */
  def evaluate(state: ProgrammingState): (GradingStatus, Option[LanguageMap[HumanLanguage]]) = {
    import ProgramDataTestGrader.text
    val execution = try Right(state.inEnvironment(environment).expression.execute(world, ProgramEntry.Call(function, arguments), limits))
    catch {
      case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
    }
    execution match {
      case Left(problem) =>
        (GradingStatus.Incorrect, Some(text(s"Das Programm kann so noch nicht ausgeführt werden: $problem",
          s"The program cannot be run yet: $problem")))
      case Right(run) => run.status match {
        case TurtleExecutionStatus.Completed =>
          expectations.iterator.flatMap(check(run, _)).nextOption() match {
            case Some(problem) => (GradingStatus.Incorrect, Some(problem))
            case None => (GradingStatus.Correct, None)
          }
        case TurtleExecutionStatus.Failed(problem) =>
          (GradingStatus.Incorrect, Some(text(s"Beim Ausführen trat ein Fehler auf: $problem", s"Running the program failed: $problem")))
        case TurtleExecutionStatus.LimitExceeded =>
          (GradingStatus.Incorrect, Some(text(
            "Das Programm wurde nach zu vielen Schritten angehalten. Gibt es eine Endlosschleife oder eine Rekursion ohne Abbruch?",
            "The program was stopped after too many steps. Is there an endless loop or a recursion without a base case?")))
        case TurtleExecutionStatus.Cancelled => (GradingStatus.NotFinished, None)
      }
    }
  }

  private def check(run: ProgramExecution, expectation: DataExpectation): Option[LanguageMap[HumanLanguage]] = {
    import DataExpectation.*
    import ProgramDataTestGrader.text
    def list(values: Seq[Int]) = values.mkString("[", ", ", "]")

    val listCommands = run.moduleCommands(BeModules.NumberList)
    val swaps = listCommands.collect { case swap: ListCommand.Swap => swap }
    val used = listCommands.map(ListOperation.of).toSet
    expectation match {
      case ListEquals(values) => Option.when(run.world.list != values.toVector)(text(
        s"Erwartet war die Liste ${list(values)}, am Ende war sie ${list(run.world.list)}.",
        s"The list should be ${list(values)}, but ended as ${list(run.world.list)}."))
      case SudokuSolved => Option.when(!run.world.sudoku.solves(world.sudoku))(text(
        if run.world.sudoku.isComplete then "Alle Felder sind gefüllt, aber eine Zeile, Spalte oder ein 3×3-Block enthält eine Zahl doppelt."
        else s"Am Ende sind noch ${run.world.sudoku.cells.count(_ == 0)} Felder leer.",
        if run.world.sudoku.isComplete then "Every cell is filled, but a row, column or 3×3 block repeats a number."
        else s"${run.world.sudoku.cells.count(_ == 0)} cells are still empty at the end."))
      case SudokuUnchanged => Option.when(run.world.sudoku.cells != world.sudoku.cells)(text(
        "Für dieses unlösbare Sudoku müssen am Ende alle eingetragenen Zahlen wieder entfernt sein.",
        "For this unsolvable sudoku, every number you placed must be removed again at the end."))
      case Returns(value) => Option.when(run.returnValue.map(_ != 0.0) != Some(value))(text(
        s"Erwartet war der Rückgabewert $value, erhalten: ${run.returnValue.fold("kein Wert")(v => (v != 0.0).toString)}.",
        s"Expected the return value $value, got ${run.returnValue.fold("no value")(v => (v != 0.0).toString)}."))
      case UsesListOperation(operation) => Option.when(!used(operation))(text(
        s"Nutze hier die Operation ${ProgramDataTestGrader.name(operation)}.", s"Use the operation ${ProgramDataTestGrader.name(operation)} here."))
      case AvoidsListOperation(operation) => Option.when(used(operation))(text(
        s"Löse diese Aufgabe ohne die Operation ${ProgramDataTestGrader.name(operation)}.",
        s"Solve this task without the operation ${ProgramDataTestGrader.name(operation)}."))
      case OnlyAdjacentSwaps => swaps.find(swap => (swap.first - swap.second).abs != 1).map(swap => text(
        s"Bubble Sort vertauscht nur benachbarte Werte, hier wurden die Positionen ${swap.first} und ${swap.second} vertauscht.",
        s"Bubble sort only swaps neighbouring values, but positions ${swap.first} and ${swap.second} were swapped."))
      case SwapCount(count) => Option.when(swaps.size != count)(text(
        s"Bubble Sort braucht für diese Liste genau $count Vertauschungen, dein Programm hat ${swaps.size} durchgeführt.",
        s"Bubble sort needs exactly $count swaps for this list, your program made ${swaps.size}."))
    }
  }
}

object ProgramDataTestGrader {
  /** Room for a naive backtracking solver on the workbook's puzzles. */
  val defaultLimits: TurtleExecutionLimits = TurtleExecutionLimits(maxSteps = 2000000, maxCallDepth = 128, maxDataCommands = 2000000)

  private def text(german: String, english: String): LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMapWithFallback[HumanLanguage](Map(German -> german, English -> english), english)

  private def name(operation: ListOperation): String = operation match {
    case ListOperation.Get => "NumberList.get"
    case ListOperation.Set => "NumberList.set"
    case ListOperation.Swap => "NumberList.swap"
    case ListOperation.Length => "NumberList.length"
  }

  /** Number of swaps bubble sort performs: the inversions of the list. */
  def inversions(values: Seq[Int]): Int =
    values.indices.map(i => (i + 1 until values.size).count(j => values(i) > values(j))).sum
}
