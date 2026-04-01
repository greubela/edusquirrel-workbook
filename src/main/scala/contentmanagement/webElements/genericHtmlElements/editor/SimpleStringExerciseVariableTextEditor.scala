package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L
import contentmanagement.webElements.HtmlAppElement
import workbook.model.interaction.*
import workbook.model.interaction.history.UpdateImportance

case class SimpleStringExerciseVariableTextEditor(
                                                   exerciseVariable: InteractionVariable[String],
                                                   monoSpace: Boolean = false
                                                 ) extends HtmlAppElement {

  private def onUserInputChanged(newDisplayedText: String): Unit = {
    val trimmed = newDisplayedText.trim

    val isBigUpdate = trimmed.isEmpty || trimmed.length < newDisplayedText.length || trimmed.endsWith(".")
    val updateType = if (isBigUpdate) UpdateImportance.MINOR else UpdateImportance.TEMPORARY

    exerciseVariable.updateStateFromUserInteraction(newDisplayedText, System.currentTimeMillis(), updateType)
  }

  private val binding = new StringEditorBinding {
    override val current: Signal[String] = exerciseVariable.interactionSignal
    override def update(nextValue: String): Unit = onUserInputChanged(nextValue)
  }

  private val baseEditor = SimpleStringTextEditor.fromBinding(
    binding = binding,
    monoSpace = monoSpace
  )

  override def getDomElement(): L.Element = {
    exerciseVariable.syncFromAll()
    baseEditor.getDomElement()
  }

}
