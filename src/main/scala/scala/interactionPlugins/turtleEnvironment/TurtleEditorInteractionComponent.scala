package scala.interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars

class TurtleEditorInteractionComponent(
  program: TurtleBlockProgram,
  dragContext: TurtleBlockDragContext,
  executionResultVar: Var[Option[TurtleExecutionResult]]
) extends InteractionComponentWithReactiveVars {

  private val editorArea = new HtmlTurtleEditorArea(program, dragContext)

  private def runProgram(): Unit = {
    val result = TurtleProgramExecutor.execute(program.toProgramState)
    executionResultVar.set(Some(result))
  }

  private val domElement =
    div(
      cls := "turtle-editor-component",
      div(
        cls := "turtle-editor-controls",
        button(
          cls := "turtle-run-button",
          typ := "button",
          "Run program",
          onClick --> (_ => runProgram())
        )
      ),
      editorArea.getDomElement(),
      div(
        cls := "turtle-preview-container",
        child <-- executionResultVar.signal.map {
          case Some(result) =>
            div(
              cls := "turtle-preview",
              unsafeHtml := result.toSvg()
            )
          case None =>
            div(cls := "turtle-preview placeholder", "Run the program to see the turtle drawing.")
        }
      )
    )

  override def getDomElement(): L.Element = domElement
}
