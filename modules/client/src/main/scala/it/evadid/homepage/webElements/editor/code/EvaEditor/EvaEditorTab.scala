package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom.SVGSVGElement

/** A language choice, not a retained editor. Only create the selected editor. */
trait EvaEditorTab {
  def language: ProgrammingLanguage
  def label: String
  def showLabel: Boolean = true
  def icon: Option[ReactiveSvgElement[SVGSVGElement]] = None
  def css: String = ""
  def convert(source: ProgrammingState): ProgrammingState = source.toLanguage(language)
  def create(state: Var[ProgrammingState], appearance: EditorAppearance,
      marker: Signal[Option[it.evadid.vm.code.errors.ProgrammingConversionError]] = Val(None),
      loading: Signal[String] = Val("Loading editor…")): HtmlAppElement
}
