package it.evadid.homepage.webElements.editor.code.program

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaActionIcons
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer.Status
import it.evadid.workbook.interactions.programming.state.ProgrammingState

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}

/** The console below Eva's editor: runs the committed program dynamically in Pyodide ([[ProgramRunner]]) and
  * shows what it printed. Whether it can run, and why not, comes from the [[ProgramAnalyzer]]; it depends only on
  * the programming state, not on the editor's language. Each mount owns its runner, whose worker starts on Run.
  * Further `actions` (such as the project download) join Run and Stop in its action bar.
  */
final class ProgrammingExecutionPanel(
    source: Signal[ProgrammingState],
    environment: ProgrammingEnvironment,
    labels: ProgrammingExecutionLabels,
    analyzer: ProgramAnalyzer = ProgramAnalyzer.shared,
    createRunner: () => ProgramRunner = () => ProgramRunner(),
    actions: Seq[Element] = Nil
) extends HtmlAppElement {
  import ProgrammingExecutionPanel.Console

  override def getDomElement(): Element = {
    val runner = createRunner()
    val console = Var[Console](Console.Idle)
    val runs = Var(0)
    val status = analyzer.statusOf(source, environment)
    /** The Python source to run, or why there is none. */
    val python: Signal[Option[Either[String, String]]] = status.map {
      case Status.Ready(analysis) => Some(analysis.python)
      case Status.Failed(reason) => Some(Left(reason))
      case Status.Pending => None
    }

    def run(code: String): Unit = {
      runs.update(_ + 1)
      val current = runs.now()
      console.set(Console.Running)
      runner.run(code).onComplete { outcome =>
        if current == runs.now() then console.set(outcome match {
          case Success(output) => Console.Finished(output)
          case Failure(error) => Console.Failed(Option(error.getMessage).getOrElse(error.toString))
        })
      }
    }

    def stop(next: Console): Unit = {
      runs.update(_ + 1)
      runner.cancel()
      console.set(next)
    }

    sectionTag(cls := "programming-execution", aria.label <-- labels.console,
      onUnmountCallback { _ =>
        runs.update(_ + 1)
        runner.close()
      },
      source.changes --> (_ => stop(Console.Idle)),
      div(cls := "programming-execution__actions eva-editor__segments", role := "toolbar", aria.label <-- labels.console,
        button(typ := "button", cls := "programming-execution__run", EvaActionIcons.run, span(child.text <-- labels.run),
          disabled <-- python.combineWith(console.signal).map((code, state) => state == Console.Running || !code.exists(_.isRight)),
          onClick.compose(_.sample(python)) --> (_.flatMap(_.toOption).foreach(code => queue.execute(() => run(code))))),
        button(typ := "button", cls := "programming-execution__stop", EvaActionIcons.stop, span(child.text <-- labels.stop),
          disabled <-- console.signal.map(_ != Console.Running),
          onClick --> (_ => stop(Console.Stopped))),
        actions),
      p(cls := "programming-execution__unavailable", role := "status",
        hidden <-- python.map(!_.exists(_.isLeft)),
        child.text <-- labels.unavailable.combineWith(python).map {
          case (text, Some(Left(reason))) => s"$text ($reason)"
          case (text, _) => text
        }),
      p(cls := "programming-execution__state", role := "status",
        child.text <-- console.signal.combineWith(labels.running, labels.stopped).map {
          case (Console.Running, running, _) => running
          case (Console.Stopped, _, stopped) => stopped
          case _ => ""
        }),
      pre(cls := "programming-execution__output", role := "log", aria.live := "polite",
        child.text <-- console.signal.map {
          case Console.Finished(output) => output.stdout
          case _ => ""
        }),
      pre(cls := "programming-execution__errors", role := "alert",
        hidden <-- console.signal.map(_.errors.isEmpty),
        child.text <-- console.signal.map(_.errors)))
  }
}

object ProgrammingExecutionPanel {
  private enum Console {
    case Idle, Running, Stopped
    case Finished(output: ProgramRunner.Output)
    case Failed(message: String)

    /** The error stream of a finished run, or the reason the run failed. */
    def errors: String = this match {
      case Finished(output) => output.stderr
      case Failed(message) => message
      case _ => ""
    }
  }
}
