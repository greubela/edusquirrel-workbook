package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom.SVGSVGElement
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** The existing method-editing UI, bound directly to class-structured source.
  * Text that parses to the current structured state is left as typed, so formatting the
  * structure cannot represent (trailing blank lines, comment placement) is not rewritten mid-edit.
  */
final class JavaStructuredMethodEditor(
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
  private val editor = new JavaMethodEditor(logger, source, reference, appearance,
    editorConfig, conversionMarker, loadingLabel, requirePublicMain = false,
    sameSource = (left, right) => left == right || ProgrammingStateJavaStructured(left) == ProgrammingStateJavaStructured(right))

  override def dismissOnOutsideClick: Boolean = false
  override def getDomElement(): Element = editor.getDomElement()
}

object JavaStructuredMethodEditor {
  def forEvaTab(logger: Logger, config: CodeMirrorEditorConfig, tabLabel: String = "Java"): EvaEditorTab = new EvaEditorTab {
    override val language = it.evadid.core.datastructures.language.AppLanguage.Java
    override val label = tabLabel
    override val showLabel = false
    override val css = " eva-editor--java"
    override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = Some(svg.svg(svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
      svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.6",
      svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
      svg.path(svg.d := "M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6")))
    override def create(state: Var[ProgrammingState], appearance: EditorAppearance,
        marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement = {
      // A language change reaches bound signals before Laminar unmounts the old tab.
      val structured = state.bimap {
        case value: ProgrammingStateJavaStructured => value
        case value @ ProgrammingStateRaw(Java, _) => value.toJavaStructured
        case _ => ProgrammingStateJavaStructured(Map.empty)
      }(identity)
      new JavaStructuredMethodEditor(logger, structured, appearance = Some(appearance),
        editorConfig = config, conversionMarker = marker, loadingLabel = loading)
    }
  }
}
