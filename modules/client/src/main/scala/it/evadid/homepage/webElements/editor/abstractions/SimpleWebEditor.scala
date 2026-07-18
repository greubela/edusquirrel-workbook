package it.evadid.homepage.webElements.editor.abstractions

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement

trait SimpleWebEditor[T, C <: WebEditorConfig] extends HtmlAppElement {

  def underlyingVar: Var[T]

  def config: Val[C]

  def editorId: String = this.getClass.getSimpleName.toLowerCase

}
