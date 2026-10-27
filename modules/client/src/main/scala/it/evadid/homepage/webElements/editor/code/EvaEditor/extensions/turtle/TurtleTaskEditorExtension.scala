package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleExecutionProvider
import it.evadid.homepage.webElements.editor.code.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge

import scala.util.Try
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TurtleTaskEditorExtension {
  private[code] def requiresRun(source: ProgrammingState): Boolean = source match {
    case java: ProgrammingStateJavaString => Try(java.isClassProgram).getOrElse(true)
    case ProgrammingStatePythonString(code) => JavaTurtleEditingBridge.hasPythonMetadata(code)
    case snap: ProgrammingStateSnapXml => JavaTurtleEditingBridge.hasSnapMetadata(snap.snapXml)
    case snap: ProgrammingStateSnapScalaModel => JavaTurtleEditingBridge.hasSnapMetadata(snap.toSnapXml.snapXml)
    case ProgrammingStateBeExpression(_: JavaTurtleVmPrograms.Root) => true
    case _ => false
  }
}

final class TurtleTaskEditorExtension(
    state: Var[ProgrammingState], target: Option[TurtleGraphic], policy: TurtleDrawingPolicy
) extends it.evadid.homepage.webElements.HtmlAppElement {
  private val execution = new TurtleProgramExecution(List(new JavaTurtleEditorExtension(state)))
  private val panel = new TurtleExecutionPanel(state, () => execution.commandsFor(state.now()), () => execution.cancel(), target, policy)
  override def getDomElement(): Element = {
    lazy val runPanel = panel.getDomElement()
    lazy val live = HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target.get, policy).getDomElement()
    val needsRun = state.signal.flatMapSwitch(source =>
      EventStream.fromFuture(Future(target.isEmpty || TurtleTaskEditorExtension.requiresRun(source)))).startWith(true)
    div(cls := "eva-editor__sidebar turtle-sidebar",
      child <-- needsRun.distinct.map(if _ then runPanel else div(cls := "turtle-live-preview", live)),
      onUnmountCallback(_ => execution.close()))
  }
}
