package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.*
import workbook.model.states.BasicVariableBasedState
import workbook.model.states.BasicVariableBasedState.*

case class SimpleTextEditor(stateToBind: Var[BasicStringState]) extends InteractionComponentWithReactiveVars {

  private val editorTextArea = textArea(
    rows := 8,
    cols := 80,
    controlled(
      value <-- stateToBind.signal.map({
        println("bind state to textbox")
        _.getStateAsString()
      }),
      onInput.mapToValue.map(str => new BasicStringState(str, e => e))
        --> stateToBind.writer
    )
  )

  private val domElement = {
    div(
      hidden <-- isHiddenVar.signal,
      cls := "simple-text-editor",
      editorTextArea
    )
  }

  override def getDomElement(): L.Element = domElement

}
