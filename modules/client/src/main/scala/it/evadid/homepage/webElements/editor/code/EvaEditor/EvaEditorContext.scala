package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.vm.code.errors.ProgrammingConversionError

/** Everything a language editor receives from Eva besides the answer: the page environment, Eva's appearance
  * and the location of the last failed conversion, which editors mark in their source.
  */
final case class EvaEditorContext(
    environment: EvaEditorEnvironment,
    appearance: EditorAppearance,
    conversionMarker: Signal[Option[ProgrammingConversionError]]
) {
  def labels: EvaEditorLabels = environment.labels
  def logger: it.evadid.util.logging.Logger = environment.logger
  def loading: Signal[String] = labels.loading
}
