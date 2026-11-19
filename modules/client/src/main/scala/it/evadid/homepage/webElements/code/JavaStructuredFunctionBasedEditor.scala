package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** The existing method-editing UI, bound directly to class-structured source. */
final class JavaStructuredFunctionBasedEditor(
    logger: Logger,
    val state: Var[ProgrammingStateJavaStructured],
    reference: Option[() => Element] = None,
    appearance: Option[EditorAppearance] = None,
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    conversionMarker: Signal[Option[ProgrammingConversionError]] = Val(None),
    loadingLabel: Signal[String] = Val("Loading editor…")
) extends HtmlAppElement {
  private val source = state.bimap[ProgrammingState](value => ProgrammingStateRaw(Java, value.toString)) {
    case ProgrammingStateRaw(Java, code) => ProgrammingStateJavaStructured(code)
    case other => other.toJavaStructured
  }
  private val editor = new JavaFunctionBasedEditor(logger, source, reference, appearance,
    editorConfig, conversionMarker, loadingLabel, requirePublicMain = false)

  override def dismissOnOutsideClick: Boolean = false
  override def getDomElement(): Element = editor.getDomElement()
}

object JavaStructuredFunctionBasedEditor {
  def forEvaTab(logger: Logger, config: CodeMirrorEditorConfig, tabLabel: String = "Java"): EvaEditorTab = new EvaEditorTab {
    override val language = it.evadid.core.datastructures.language.AppLanguage.Java
    override val label = tabLabel
    override val css = " eva-editor--java"
    override def create(state: Var[ProgrammingState], appearance: EditorAppearance,
        marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement = {
      // A language change reaches bound signals before Laminar unmounts the old tab.
      val structured = state.bimap {
        case value: ProgrammingStateJavaStructured => value
        case value @ ProgrammingStateRaw(Java, _) => value.toJavaStructured
        case _ => ProgrammingStateJavaStructured(Map.empty)
      }(identity)
      new JavaStructuredFunctionBasedEditor(logger, structured, appearance = Some(appearance),
        editorConfig = config, conversionMarker = marker, loadingLabel = loading)
    }
  }
}
