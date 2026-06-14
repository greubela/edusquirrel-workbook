package it.evadid.homepage.webElements.editor

import com.raquo.airstream.core.Signal
import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L.*
import SimpleTextEditor.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.interaction.sync.UpdateImportance

object SimpleTextEditor {
  case class TextEditorConfig(monospace: Boolean, rowsCount: Int, colsCount: Int, containerClass: String)

  val defaultConfig: TextEditorConfig = TextEditorConfig(
    monospace = false,
    rowsCount = 4,
    colsCount = 110,
    containerClass = "simple-text-editor"
  )
}

case class SimpleTextEditor(
                             varToBind: Var[String],
                             config: Var[TextEditorConfig] = Var(defaultConfig),
                           ) extends HtmlAppElement {

  override def getDomElement(): Element = domElement

  private val domElement: Element = {
    div(
      cls := "simple-text-editor",
      child <-- config.signal.map(createTextEditor)
    )
  }

  private def createTextEditor(curConfig: TextEditorConfig): Element = textArea(
    rows := curConfig.rowsCount,
    cols := curConfig.colsCount,
    cls := curConfig.containerClass,
    if (curConfig.monospace) cls := "mono" else cls := "",
    controlled(
      value <-- varToBind.signal,
      onInput.mapToValue --> varToBind.writer
    )
  )

}
