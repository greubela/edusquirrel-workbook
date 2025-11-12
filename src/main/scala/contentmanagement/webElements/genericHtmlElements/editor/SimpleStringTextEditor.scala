package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.*
import workbook.model.states.BasicVariableBasedState
import workbook.model.states.BasicVariableBasedState.*

case class SimpleStringTextEditor(stateToBind: Var[String]) extends HtmlAppElement{

  private val editorTextArea = textArea(
    rows := 8,
    cols := 80,
    controlled(
      value <-- stateToBind.signal,
      onInput.mapToValue        --> stateToBind.writer
    )
  )

  private val domElement = {
    div(
      cls := "simple-text-editor",
      editorTextArea
    )
  }

  def getDomElement(): L.Element = domElement

}
