package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.workbook.elements.interactionElements.programming.state.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** Console execution of the current programming state through its shared BeExpression derivation. */
final class ProgrammingExecutionPanel(source: Var[ProgrammingState], label: String => Signal[String]) extends HtmlAppElement {
  private val running = Var(false)
  private val output = Var("")
  private val revision = Var(0L)
  private val available: Signal[Option[ProgrammingStateBeExpression]] = source.signal.flatMapSwitch { state =>
    Signal.fromFuture(Future(Try {
      val converted = state.toBeExpression
      require(!converted.expression.subtree.exists(_.isInstanceOf[BeSourcePlaceholder]))
      converted
    }.toOption), Option.empty[ProgrammingStateBeExpression])
  }
  private def run(): Unit = {
    val original = source.now()
    revision.update(_ + 1)
    val token = revision.now()
    running.set(true)
    output.set("")
    Future(original.toBeExpression.programExecution).onComplete { result =>
      if token == revision.now() && original == source.now() then {
        running.set(false)
        output.set(result.fold(error => Option(error.getMessage).getOrElse(error.toString), execution =>
          execution.moduleCommands(it.evadid.vm.modules.BeModules.System).map(_.text).mkString + (if execution.completed then "" else s"\n${execution.status}")))
      }
    }
  }
  override def getDomElement(): Element = sectionTag(cls := "programming-execution", aria.label <-- label("console"),
    button(typ := "button", child.text <-- label("run"),
      disabled <-- available.combineWith(running.signal).map((program, busy) => busy || program.isEmpty),
      onClick --> (_ => queue.execute(() => run()))),
    p(role := "status", hidden <-- available.map(_.nonEmpty), child.text <-- label("executionUnavailable")),
    pre(role := "log", aria.live := "polite", child.text <-- output.signal),
    onUnmountCallback { _ => revision.update(_ + 1); running.set(false) },
    source.signal.changes --> { _ => revision.update(_ + 1); running.set(false); output.set("") })
}
