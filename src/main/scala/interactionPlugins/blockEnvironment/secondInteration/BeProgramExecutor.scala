package interactionPlugins.blockEnvironment.secondInteration

import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.unsafeWindowOwner
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class BeProgramExecutor(workspaceState: BeWorkspaceState) extends HtmlWorkbookElement {

  private val runBus = new EventBus[Unit]
  private val outputVar = Var(List.empty[String])

  private val domElement =
    div(
      cls := "be-program-executor",
      button(
        cls := "be-executor-run",
        "Run Program",
        onClick.mapTo(()) --> runBus.writer
      ),
      pre(
        cls := "be-executor-code",
        child.text <-- workspaceState.programSignal.map(_.toPythonString)
      ),
      ul(
        cls := "be-executor-output",
        children <-- outputVar.signal.map(_.map(msg => li(msg)))
      )
    )

  runBus.events.withCurrentValueOf(workspaceState.programSignal).foreach { case (_, program) =>
    val code = program.toPythonString
    val lines = code.split('\n').toList
    outputVar.update(output => ("Program generated:" +: lines) ::: output)
  }(unsafeWindowOwner)

  override def getDomElement(): L.Element = domElement
}
