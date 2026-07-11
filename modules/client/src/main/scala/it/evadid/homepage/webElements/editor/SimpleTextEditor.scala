package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.SimpleTextEditor.*

case class SimpleTextEditor(
                             varToBind: Var[String],
                             config: Var[TextEditorConfig] = Var(defaultConfig),
                           ) extends HtmlAppElement {

  override def getDomElement(): Element = domElement

  private val domElement: Element = createTextEditor(config.signal)

  def createTextEditor(config: Signal[TextEditorConfig]): Element = textArea(
    rows <-- config.map(_.rowsCount),
    cols <-- config.map(_.colsCount),
    cls <-- config.map(curConfig =>  s" simple-text-editor-textarea ${curConfig.containerClass}${if(curConfig.monospace) " mono" else ""}"),
    controlled(
      value <-- varToBind.signal,
      onInput.mapToValue --> varToBind.writer
    )
  )

}

object SimpleTextEditor {
  case class TextEditorConfig(monospace: Boolean, rowsCount: Int, colsCount: Int, containerClass: String)

  val defaultConfig: TextEditorConfig = TextEditorConfig(
    monospace = false,
    rowsCount = 4,
    colsCount = 110,
    containerClass = "simple-text-editor"
  )
}