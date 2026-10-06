package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSource}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingStateJavaString
import todomove.datastructures.web.file.FullImage

import java.util.concurrent.{CancellationException, TimeoutException}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class JavaEditor(
    val state: Var[ProgrammingStateJavaString],
    onStateEdited: ProgrammingStateJavaString => Unit = _ => (),
    runnerFactory: () => JavaEditorSession.Runner = () => JavaEditorSession.defaultRunner()
) extends HtmlAppElement with FullscreenLifecycle {
  private val textState = Var(state.now().code)
  private val session = new JavaEditorSession(state.now(), runnerFactory)
  private var mounted = false

  private val codeEditor = CodeMirrorEditor(
    textState,
    code => {
      val next = ProgrammingStateJavaString(code)
      session.updateSource(next)
      state.set(next)
      onStateEdited(next)
    },
    language = AppLanguage.Java
  )
  private lazy val codeElement = codeEditor.getDomElement()

  private def receive(next: ProgrammingStateJavaString): Unit = {
    session.updateSource(next)
    if textState.now() != next.code then textState.set(next.code)
  }

  def run(): Future[T.Execution] = {
    receive(state.now())
    val source = state.now()
    val owner = new ManualOwner
    var changed = false
    state.signal.changes.foreach { next =>
      if next != source then changed = true
      receive(next)
    }(using owner)
    session.run().transform { result =>
      if changed || state.now() != source then {
        receive(state.now())
        scala.util.Failure(new CancellationException("Java source changed."))
      } else result
    }.andThen { case _ =>
      owner.killSubscriptions()
      if !mounted && session.status.now() != JavaEditorSession.State.Running then session.release()
    }
  }

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    run().flatMap(result => JavaEditor.commands(result).fold(
      message => Future.failed(IllegalStateException(message)), Future.successful
    ))

  def stop(): Unit = session.stop()

  override def onFullscreenClose(): Unit = session.release()
  override def dismissOnOutsideClick: Boolean = false

  override def getDomElement(): Element = element

  private lazy val element: Element = div(
    cls := "java-editor",
    state.signal --> receive,
    session.status.signal --> { status =>
      status match
        case JavaEditorSession.State.Invalid(diagnostic) =>
          codeEditor.setDiagnostics(JavaEditor.diagnostics(session.source.code, diagnostic))
        case _ => codeEditor.clearDiagnostics()
    },
    onMountCallback { _ => mounted = true },
    onUnmountCallback { _ =>
      mounted = false
      session.release()
    },
    div(
      cls := "java-editor__toolbar",
      button(
        typ := "button",
        disabled <-- session.status.signal.map(_ == JavaEditorSession.State.Running),
        "Run Java",
        onClick --> (_ => run().onComplete(_ => ()))
      ),
      button(
        typ := "button",
        disabled <-- session.status.signal.map(_ != JavaEditorSession.State.Running),
        "Stop",
        onClick --> (_ => stop())
      )
    ),
    div(cls := "java-editor__code", codeElement),
    div(
      cls := "java-editor__result",
      div(
        cls := "java-editor__message",
        role := "status",
        aria.live := "polite",
        text <-- session.status.signal.map(JavaEditor.message)
      ),
      child.maybe <-- session.status.signal.map {
        case JavaEditorSession.State.Finished(result) if result.commands.nonEmpty =>
          val drawing = TurtlePathBuilder[Double](Point(0, 0), JavaEditor.turtleCommands(result), 0.0)
          Some(div(
            cls := "java-editor__drawing",
            aria.label := "Java turtle drawing",
            FullImage(drawing.svgPathBuilder).newDomImage
          ))
        case _ => None
      }
    )
  )
}

object JavaEditor {
  private[code] def turtleCommands(result: T.Execution): List[TurtleCommand[Double]] =
    result.commands.map(command => TurtleCommand[Double](
      if command.command == R.TurtleCommand.Forward then "forward" else "right",
      List(command.value.toDouble)
    )).toList

  private[code] def commands(result: T.Execution): Either[String, List[TurtleCommand[Double]]] =
    if result.status == T.Status.Completed then Right(turtleCommands(result))
    else Left(executionMessage(result))

  private def executionMessage(result: T.Execution): String = result.status match {
    case T.Status.Completed if result.commands.isEmpty => "Your program finished without moving the turtle."
    case T.Status.Completed => "Here is your drawing."
    case T.Status.LimitExceeded => "Your drawing is unfinished. The program reached its execution limit; check whether your loops can stop."
    case T.Status.Cancelled => "You stopped the program."
    case T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)) => "Your program stopped because it tried to divide by zero."
    case T.Status.Failed(_) => "Your program could not finish. Check your code and run it again."
  }

  private[code] def message(state: JavaEditorSession.State): String = state match {
    case JavaEditorSession.State.Idle => "Run your program to see its drawing."
    case JavaEditorSession.State.Running => "Your program is running…"
    case JavaEditorSession.State.Invalid(diagnostic) => diagnostic.message
    case JavaEditorSession.State.Finished(result) => executionMessage(result)
    case JavaEditorSession.State.Stopped => "You stopped the program."
    case JavaEditorSession.State.Failed(_: CancellationException) => "You stopped the program."
    case JavaEditorSession.State.Failed(_: TimeoutException) => "Your program took too long. Check your loops, then try again."
    case JavaEditorSession.State.Failed(_) => "Java execution could not finish. Please try running your program again."
  }

  private[code] def diagnostics(source: String, diagnostic: JavaTurtleSource.Diagnostic): Seq[CodeMirrorEditor.Diagnostic] =
    diagnostic.range.toSeq.map { range =>
      def position(offset: Int): (Int, Int) = {
        val target = math.max(0, math.min(offset, source.length))
        val breaks = "\r\n|\r|\n".r.findAllMatchIn(source).filter(_.end <= target).toList
        val line = breaks.size + 1
        val start = breaks.lastOption.fold(0)(_.end)
        val end = "\r\n|\r|\n".r.findFirstMatchIn(source.substring(start)).fold(source.length)(_.start + start)
        (line, math.min(target - start, end - start))
      }
      val (line, from) = position(range.start)
      val (endLine, to) = position(math.max(range.start, range.end))
      CodeMirrorEditor.Diagnostic(line, Some(endLine), Some(from), Some(to), diagnostic.message, "error")
    }
}
