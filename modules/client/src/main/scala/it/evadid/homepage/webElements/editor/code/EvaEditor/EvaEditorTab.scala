package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.conversion.ConversionResult
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom.SVGSVGElement

/** A language choice, not a retained editor. Only create the selected editor. */
trait EvaEditorTab {
  def language: ProgrammingLanguage
  /** The tab's caption; language names stay untranslated, descriptive captions come from `labels`. */
  def label(labels: EvaEditorLabels): Signal[String] = Val(language.name)
  def showLabel: Boolean = true
  def icon: Option[ReactiveSvgElement[SVGSVGElement]] = None
  def css: String = ""
  def convert(source: ProgrammingState, environment: ProgrammingEnvironment): ConversionResult =
    source.inEnvironment(environment).convertToLanguage(language)
  def create(state: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement
}
