package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.abstractions.*
import workbook.model.abstractions.InteractionComponent.InteractionComponentWithReactiveVars

case class SimpleTextDisplay(displaySignal: Signal[Option[String]]) extends InteractionComponentWithReactiveVars(false, false, false) {
  

  private val editorTextArea = textArea(
    rows := 8,
    cols := 80,
    disabled := true,
    value <-- displaySignal.map(_.getOrElse("[no data]")),
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
