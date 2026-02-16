package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionComponent

case class SimpleTextDisplay(displaySignal: Signal[Option[String]]) extends InteractionComponent {


  val isHiddenVar: Var[Boolean] = Var(false)

  def setHighlight(highlight: Boolean): Unit = {}

  def setVisible(visible: Boolean): Unit = isHiddenVar.set(!visible)

  def setDisabled(disabled: Boolean): Unit = {}

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
