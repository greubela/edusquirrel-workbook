package it.evadid.homepage.webElements.editor.code.program

import com.raquo.laminar.api.L.*
import it.evadid.vm.analysis.SourceWarning

/** Language-independent source diagnostics, resolved from the blockeditor map. */
final class SourceWarningLabels(text: String => Signal[String]) {
  val all: Signal[Map[SourceWarning.Kind, String]] = Signal.combine(
    text("sourceUnknownUse"), text("sourceUnreadable"), text("sourceUnsupported")).map { (unknown, unreadable, unsupported) =>
      Map(SourceWarning.Kind.UnknownDependency -> unknown, SourceWarning.Kind.Unreadable -> unreadable,
        SourceWarning.Kind.Unsupported -> unsupported)
    }
}
