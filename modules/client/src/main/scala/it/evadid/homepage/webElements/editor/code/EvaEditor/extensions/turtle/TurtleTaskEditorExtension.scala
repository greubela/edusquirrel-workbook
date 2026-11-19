package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleExecutionProvider
import it.evadid.homepage.webElements.editor.code.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{CompareShapePreview, HtmlTurtleRecreateShapeRenderer}
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.*

import scala.util.Try
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TurtleTaskEditorExtension {
  private[code] def requiresRun(source: ProgrammingState): Boolean = source match {
    case java @ ProgrammingStateRaw(Java, _) => Try(java.isClassProgram).getOrElse(true)
    case _: ProgrammingStateJavaStructured => true
    case _ => false
  }
}

final class TurtleTaskEditorExtension(
    state: Var[ProgrammingState], target: Option[TurtleGraphic], sharedExecution: Boolean = false
) extends it.evadid.homepage.webElements.HtmlAppElement {
  private val execution = new TurtleProgramExecution(if sharedExecution then Seq.empty else Seq(new JavaTurtleEditorExtension(state)),
    sharedInterpreter = sharedExecution)
  private val panel = new TurtleExecutionPanel(state, () => execution.commandsFor(state.now()), () => execution.cancel(), target)
  override def getDomElement(): Element = {
    lazy val runPanel = panel.getDomElement()
    lazy val live = CompareShapePreview(state, target.get).getDomElement()
    val needsRun = state.signal.flatMapSwitch(source =>
      EventStream.fromFuture(Future(target.isEmpty || TurtleTaskEditorExtension.requiresRun(source)))).startWith(true)
    div(cls := "eva-editor__sidebar turtle-sidebar",
      child <-- needsRun.distinct.map(if _ then runPanel else div(cls := "turtle-live-preview", live)),
      onUnmountCallback(_ => execution.close()))
  }
}
