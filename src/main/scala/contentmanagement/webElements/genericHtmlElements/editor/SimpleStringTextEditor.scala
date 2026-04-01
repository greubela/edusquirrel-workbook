package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement

trait StringEditorBinding {
  def current: Signal[String]
  def update(nextValue: String): Unit
}

object SimpleStringTextEditor {

  def fromBinding(
                   binding: StringEditorBinding,
                   monoSpace: Boolean = false,
                   rowsCount: Int = 8,
                   colsCount: Int = 80,
                   containerClass: String = "simple-text-editor workbook-interaction"
                 ): HtmlAppElement = {
    SimpleStringTextEditorBinding(
      binding = binding,
      monoSpace = monoSpace,
      rowsCount = rowsCount,
      colsCount = colsCount,
      containerClass = containerClass
    )
  }

  private case class SimpleStringTextEditorBinding(
                                                     binding: StringEditorBinding,
                                                     monoSpace: Boolean,
                                                     rowsCount: Int,
                                                     colsCount: Int,
                                                     containerClass: String
                                                   ) extends HtmlAppElement {
    private val editorTextArea = textArea(
      rows := rowsCount,
      cols := colsCount,
      cls := containerClass,
      if (monoSpace) cls := "mono" else cls := "",
      controlled(
        value <-- binding.current,
        onInput.mapToValue --> binding.update
      )
    )

    override def getDomElement(): L.Element = editorTextArea
  }
}

case class SimpleStringTextEditor(
                                   stateToBind: Var[String],
                                   monoSpace: Boolean = false,
                                   onUserInput: String => Unit = _ => ()
                                 ) extends HtmlAppElement {

  private val binding = new StringEditorBinding {
    override val current: Signal[String] = stateToBind.signal
    override def update(nextValue: String): Unit = {
      onUserInput(nextValue)
      stateToBind.set(nextValue)
    }
  }

  private val baseEditor = SimpleStringTextEditor.fromBinding(
    binding = binding,
    monoSpace = monoSpace
  )

  override def getDomElement(): L.Element = baseEditor.getDomElement()

}
