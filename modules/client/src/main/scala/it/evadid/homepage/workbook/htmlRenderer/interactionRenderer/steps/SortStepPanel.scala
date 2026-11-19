package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.simulation.{ProgramEntry, ProgramExecution}
import it.evadid.vm.simulation.data.{DataWorld, ListCommand}
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Replays the list operations of `sortFunction` on `example`, one recorded `ListCommand` per step, as bars.
 * By default only changes (set, swap) are steps; reads can be included. Highlighted bars show the
 * positions the current step read or changed.
 */
final class SortStepPanel(state: Var[ProgrammingState], sortFunction: String, example: Seq[Int],
    text: String => Signal[String]) extends HtmlAppElement {
  private given ExecutionContext = ExecutionContext.global
  private val showReads = Var(false)

  private val executions: Signal[Option[Either[String, ProgramExecution]]] =
    state.signal.changes.debounce(300).toSignal(state.now()).flatMapSwitch { value =>
      Signal.fromFuture(Future {
        try Right(value.toBeExpression.execute(DataWorld(list = example.toVector), ProgramEntry.Call(sortFunction)))
        catch { case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)) }
      })
    }

  private val replay: Signal[SortStepPanel.Replay] = executions.combineWith(showReads.signal).map { (execution, reads) =>
    SortStepPanel.Replay(example.toVector, execution.flatMap(_.toOption).fold(Nil)(_.listCommands), reads)
  }.distinct

  private val navigator = new StepNavigator(replay.map(_.steps.size), text)

  private def bars(replay: SortStepPanel.Replay, step: Int): Element = {
    val values = replay.listAt(step)
    val (reads, changed) = replay.highlights(step)
    val low = (values :+ 0).min
    val range = ((values :+ 0).max - low).max(1)
    val sortedAtEnd = step == replay.steps.size && values == values.sorted
    ol(cls := s"sort-steps${if sortedAtEnd then " sort-steps--sorted" else ""}", aria.label <-- text("programDataList"),
      values.zipWithIndex.map { (value, index) =>
        val emphasis = if changed(index) then " sort-bar--changed" else if reads(index) then " sort-bar--read" else ""
        li(cls := s"sort-bar$emphasis",
          div(cls := "sort-bar__column", div(cls := "sort-bar__fill",
            styleAttr := s"--sort-bar-height: ${math.round(8 + 92.0 * (value - low) / range)}%")),
          span(cls := "sort-bar__value", value.toString),
          span(cls := "sort-bar__index", index.toString))
      })
  }

  private def caption(replay: SortStepPanel.Replay, step: Int): Signal[String] =
    if step == 0 then text("sortStepStart")
    else {
      val (key, values) = replay.steps(step - 1) match {
        case ListCommand.Get(index, value) => ("sortStepGet", Map("index" -> index, "value" -> value))
        case ListCommand.Set(index, value) => ("sortStepSet", Map("index" -> index, "value" -> value))
        case ListCommand.Swap(first, second) => ("sortStepSwap", Map("first" -> first, "second" -> second))
        case ListCommand.Length(length) => ("sortStepLength", Map("length" -> length))
      }
      text(key).map(template => values.foldLeft(template) { case (current, (name, value)) => current.replace(s"{$name}", value.toString) })
    }

  override def getDomElement(): Element = div(cls := "step-panel sort-step-panel",
    child <-- replay.combineWith(navigator.step).map(bars),
    navigator.element,
    p(cls := "step-panel__caption", aria.live := "polite", child.text <-- replay.combineWith(navigator.step).flatMapSwitch(caption)),
    div(cls := "step-panel__footer",
      label(cls := "step-panel__toggle",
        input(typ := "checkbox", checked <-- showReads.signal, onChange.mapToChecked --> showReads.writer),
        span(child.text <-- text("sortShowReads"))),
      span(cls := "step-panel__stats", child.text <-- replay.combineWith(text("sortStepStats")).map { (value, template) =>
        template.replace("{swaps}", value.count(_.isInstanceOf[ListCommand.Swap]).toString)
          .replace("{sets}", value.count(_.isInstanceOf[ListCommand.Set]).toString)
          .replace("{reads}", value.count(command => command.isInstanceOf[ListCommand.Get]).toString)
      })),
    child.maybe <-- executions.map {
      case Some(Left(message)) => Some(p(cls := "step-panel__problem", child.text <-- text("programDataUnavailable").map(prefix => s"$prefix $message")))
      case Some(Right(execution)) => execution.status match {
        case TurtleExecutionStatus.Failed(message) => Some(p(cls := "step-panel__problem", child.text <-- text("programDataFailed").map(prefix => s"$prefix $message")))
        case TurtleExecutionStatus.LimitExceeded => Some(p(cls := "step-panel__problem", child.text <-- text("programDataLimit")))
        case _ => None
      }
      case None => None
    })
}

object SortStepPanel {
  /** The list before and after every recorded command; `steps` are the commands shown as steps. */
  final case class Replay(initial: Vector[Int], commands: List[ListCommand], includeReads: Boolean) {
    private val lists: Vector[Vector[Int]] = commands.scanLeft(initial) { (list, command) => command match {
      case ListCommand.Set(index, value) => list.updated(index, value)
      case ListCommand.Swap(first, second) => list.updated(first, list(second)).updated(second, list(first))
      case _ => list
    }}.toVector
    private val shown: Vector[Int] = commands.indices.filter(index => includeReads || (commands(index) match {
      case _: ListCommand.Set | _: ListCommand.Swap => true
      case _ => false
    })).toVector

    val steps: Vector[ListCommand] = shown.map(commands(_))
    def count(predicate: ListCommand => Boolean): Int = commands.count(predicate)

    /** The list after `step` shown steps; the last step shows the final list. */
    def listAt(step: Int): Vector[Int] = if step == 0 then initial else lists(shown(step - 1) + 1)

    /** Positions read and positions changed by the shown step. */
    def highlights(step: Int): (Set[Int], Set[Int]) =
      if step == 0 then (Set.empty, Set.empty)
      else steps(step - 1) match {
        case ListCommand.Get(index, _) => (Set(index), Set.empty)
        case ListCommand.Set(index, _) => (Set.empty, Set(index))
        case ListCommand.Swap(first, second) => (Set.empty, Set(first, second))
        case ListCommand.Length(_) => (Set.empty, Set.empty)
      }
  }
}
