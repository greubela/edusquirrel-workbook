package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaConversionFailure, EvaEditorContext, EvaEditorEnvironment, EvaEditorLabels}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger


/** Eva environments and editor contexts for specs: a silent logger, English and the given labels. */
object EvaTestContext {
  private val logger = Logger.withNameAndPrefixes(Some("eva-spec"), PrintToStdLogger.printNothing)

  def environment(labels: EvaEditorLabels = EvaEditorLabels.fromContent(_ => Val("_"))): EvaEditorEnvironment =
    EvaEditorEnvironment(logger, labels, Val(English))

  def apply(labels: EvaEditorLabels = EvaEditorLabels.fromContent(_ => Val("_")),
      marker: Signal[Option[EvaConversionFailure]] = Val(None)): EvaEditorContext =
    EvaEditorContext(environment(labels), EditorAppearance(), marker)
}
