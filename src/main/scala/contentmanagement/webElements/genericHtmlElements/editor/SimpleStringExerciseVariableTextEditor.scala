package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import workbook.model.exercise.ExerciseVariable
import workbook.model.exercise.ExerciseVariable.UpdateImportance

case class SimpleStringExerciseVariableTextEditor(
                                                   exerciseVariable: ExerciseVariable[String],
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
    controlled(
      value <-- exerciseVariable.asVar.signal,
      onInput.mapToValue --> { value => onUserInputChanged(value)
      }
    )
  )

  private val domElement = {
    div(
      cls := "simple-text-editor",
      editorTextArea
    )
  }

  def getDomElement(): L.Element = {
    exerciseVariable.syncFromAll()
    domElement
  }

}
