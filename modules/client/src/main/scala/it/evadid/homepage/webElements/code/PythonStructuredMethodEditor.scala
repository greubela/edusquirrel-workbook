package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.Python
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** The structure editor for Python, bound to class-structured source: the class diagram shows the module's
  * functions and the classes, the code editor one function or method at a time. Text that parses to the
  * current structured state is left as typed, as in [[JavaStructuredMethodEditor]].
  */
final class PythonStructuredMethodEditor(
    logger: Logger,
    val state: Var[ProgrammingStatePythonStructured],
    appearance: Option[EditorAppearance] = None,
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    conversionMarker: Signal[Option[ProgrammingConversionError]] = Val(None),
    loadingLabel: Signal[String] = Val("Loading editor…"),
    labels: JavaEditorLabels = JavaEditorLabels.keys,
    features: JavaStructureFeatures = JavaStructureFeatures.Full
) extends HtmlAppElement {
  private val source = state.bimap[ProgrammingState](value => ProgrammingStateRaw(Python, value.toString)) {
    case ProgrammingStateRaw(Python, code) => ProgrammingStatePythonStructured(code)
    case other => other.toPythonStructured
  }
  private val editor = new JavaMethodEditor(logger, source, None, appearance, editorConfig, conversionMarker, loadingLabel,
    requirePublicMain = false,
    sameSource = (left, right) => left == right || ProgrammingStatePythonStructured(left) == ProgrammingStatePythonStructured(right),
    labels = labels, features = features, dialect = StructureDialect.PythonDialect)

  override def dismissOnOutsideClick: Boolean = false
  override def getDomElement(): Element = editor.getDomElement()
}

object PythonStructuredMethodEditor {
  def forEvaTab(logger: Logger, config: CodeMirrorEditorConfig, tabLabel: String = "Python",
      labels: JavaEditorLabels = JavaEditorLabels.keys, features: JavaStructureFeatures = JavaStructureFeatures.Full): EvaEditorTab =
    new EvaEditorTab {
      override val language = Python
      override val label = tabLabel
      override def create(state: Var[ProgrammingState], appearance: EditorAppearance,
          marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement = {
        // A language change reaches bound signals before Laminar unmounts the old tab.
        val structured = state.bimap {
          case value: ProgrammingStatePythonStructured => value
          case value @ ProgrammingStateRaw(Python, _) => value.toPythonStructured
          case _ => ProgrammingStatePythonStructured("")
        }(identity)
        new PythonStructuredMethodEditor(logger, structured, appearance = Some(appearance), editorConfig = config,
          conversionMarker = marker, loadingLabel = loading, labels = labels, features = features)
      }
    }
}
