package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor

/** Everything a language editor receives from Eva besides the answer: the page environment, Eva's appearance
  * and the last failed conversion, which editors mark in their source.
  */
final case class EvaEditorContext(
    environment: EvaEditorEnvironment,
    appearance: EditorAppearance,
    conversionMarker: Signal[Option[EvaConversionFailure]]
) {
  def labels: EvaEditorLabels = environment.labels
  def logger: it.evadid.util.logging.Logger = environment.logger
  def loading: Signal[String] = labels.loading

  /** The failed conversion's location in a source editor, explained in the page language. */
  def conversionDiagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] =
    conversionMarker.combineWith(labels.conversion).map((failure, texts) =>
      failure.toSeq.flatMap(value => CodeMirrorEditor.mark(value.error, texts.describe(value))))
}
