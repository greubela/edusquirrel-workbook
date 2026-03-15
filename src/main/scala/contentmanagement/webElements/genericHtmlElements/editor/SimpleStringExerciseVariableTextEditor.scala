package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import workbook.model.interaction.*
import workbook.model.interaction.history.UpdateImportance

case class SimpleStringExerciseVariableTextEditor(
                                                   exerciseVariable: InteractionVariable[String],
                                                   monoSpace: Boolean = false
                                                 ) extends HtmlAppElement {

  def onUserInputChanged(newDisplayedText: String): Unit = {
    val trimmed = newDisplayedText.trim

    val isBigUpdate = trimmed.isEmpty || trimmed.length < newDisplayedText.length || trimmed.endsWith(".")
    val updateType = if (isBigUpdate) UpdateImportance.MINOR else UpdateImportance.TEMPORARY

    exerciseVariable.updateStateFromUserInteraction(newDisplayedText, System.currentTimeMillis(), updateType)

  }

  private val editorTextArea = textArea(
    rows := 8,
    cols := 80,
    cls := "simple-text-editor workbook-interaction",
    if (monoSpace) cls := "mono" else cls := "",
    controlled(
      value <-- exerciseVariable.interactionSignal,
      onInput.mapToValue --> { value => onUserInputChanged(value)
      }
    )
  )

  private val domElement = {
    div(
      cls := "simple-text-editor workbook-interaction",
      editorTextArea
    )
  }

  def getDomElement(): L.Element = {
    exerciseVariable.syncFromAll()
    editorTextArea
  }

}
