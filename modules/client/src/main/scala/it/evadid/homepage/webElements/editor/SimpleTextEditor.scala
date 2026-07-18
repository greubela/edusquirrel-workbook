package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.config.TextEditorConfig

case class SimpleTextEditor(
                             underlyingVar: Var[String],
                             config: Val[TextEditorConfig] = Val(TextEditorConfig.defaultConfig),
                           ) extends SimpleWebEditor[String, TextEditorConfig] {

  override def getDomElement(): Element = domElement

  private val domElement: Element = createTextEditor(config)

  def createTextEditor(config: Signal[TextEditorConfig]): Element = textArea(
    rows <-- config.map(_.rowsCount),
    cols <-- config.map(_.colsCount),
    placeholder <-- config.flatMapSwitch(curConfig => laminarHelper.plaintextStringSignal(curConfig.placeholder)),
    cls <-- config.map(_.inputCssClassStr(List("simple-text-editor-textarea"))),
    controlled(
      value <-- underlyingVar.signal,
      onInput.mapToValue --> underlyingVar.writer
    )
  )

}
