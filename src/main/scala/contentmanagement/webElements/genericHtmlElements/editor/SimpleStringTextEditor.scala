package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.*

case class SimpleStringTextEditor(
                                   stateToBind: Var[String],
                                   monoSpace: Boolean = false,
                                   onUserInput: String => Unit = _ => ()
                                 ) extends HtmlAppElement{

  private val editorTextArea = textArea(
    rows := 8,
    cols := 80,
    cls := "simple-text-editor workbook-interaction",
    if (monoSpace) cls := "mono" else cls := "",
    controlled(
      value <-- stateToBind.signal,
      onInput.mapToValue --> { value =>
        onUserInput(value)
        stateToBind.set(value)
      }
    )
  )

  private val domElement = {
    div(
      cls := "simple-text-editor",
      editorTextArea
    )
  }

  def getDomElement(): L.Element = editorTextArea

}
