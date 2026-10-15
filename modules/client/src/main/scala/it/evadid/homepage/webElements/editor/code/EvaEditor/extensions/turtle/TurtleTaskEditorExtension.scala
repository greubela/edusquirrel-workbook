package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorExtension, EvaEditorModule}
import it.evadid.homepage.webElements.editor.code.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge

import scala.util.Try

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
    state: Var[ProgrammingState],
    target: Option[TurtleGraphic],
    policy: TurtleDrawingPolicy
) extends EvaEditorExtension with EvaEditorModule{
  private val view = Var(Option.empty[TurtleExecutionPanel])

  override def panel(context: EvaEditorExtension.Context): Option[Element] = {
    val current = view.now().getOrElse {
      val captured = Var(state.now())
      val created = new TurtleExecutionPanel(state, () => context.execute(captured.now()), context.cancel,
        target, policy, () => { captured.set(context.captureSource()) })
      view.set(Some(created))
      created
    }
    Some(div(cls := "eva-editor__sidebar turtle-sidebar",
      child <-- state.signal.map(source => target.isEmpty || TurtleTaskEditorExtension.requiresRun(source)).distinct.map {
        case true => current.getDomElement()
        case false => div(cls := "turtle-live-preview",
          HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target.get, policy).getDomElement())
      }))
  }

  override def close(): Unit = view.now().foreach(_.reset())

  private val context = Var(Option.empty[EvaEditorModule.Context])
  private val execution = Var(Option.empty[TurtleProgramExecution])
  override def bind(value: EvaEditorModule.Context): Unit = {
    context.set(Some(value))
    execution.set(Some(new TurtleProgramExecution(value.modules.collect { case extension: EvaEditorExtension => extension }.toList)))
  }
  override lazy val getDomElement: Option[L.Element] = {
    val bound = context.now().getOrElse(throw IllegalStateException("The turtle module must be bound before rendering."))
    panel(EvaEditorExtension.Context(bound.captureSource, source => execution.now().get.commandsFor(source), () => execution.now().get.cancel()))
  }
  override def onFullscreenClose(): Unit = { close(); execution.now().foreach(_.close()) }
}
