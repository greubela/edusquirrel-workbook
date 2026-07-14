package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
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
    placeholder <-- config.flatMapSwitch(curConfig => laminarHelper.plaintextStringSignal(curConfig.placeholder)),
    cls <-- config.map(_.cssStr),
    controlled(
      value <-- varToBind.signal,
      onInput.mapToValue --> varToBind.writer
    )
  )

}

object SimpleTextEditor {
  case class TextEditorConfig(monospace: Boolean, rowsCount: Int, colsCount: Int, placeholder: LanguageMapContentId, cssClasses: List[String]) {
    lazy val cssMonoStr: String = if(monospace) " mono" else ""
    lazy val cssStr: String = cssClasses.mkString("simple-text-editor-textarea ", " ", "") + cssMonoStr
  }

  val defaultConfig: TextEditorConfig = TextEditorConfig(
    monospace = false,
    rowsCount = 4,
    colsCount = 110,
    placeholder = LanguageMapContentId("basic/textEditorPlaceholder"),
    cssClasses = List()
  )
}