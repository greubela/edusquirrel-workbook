package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.facades.CodeMirror.{CodeMirrorHandle, EditorConfig}

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.facades.CodeMirror
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.*

case class CodeMirrorEditor(
    logger: Logger,
    underlyingVar: Var[String],
    language: ProgrammingLanguage,
    config: Var[CodeMirrorEditorConfig],
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {
  import CodeMirrorEditor.*

  private val handle = Var(Option.empty[CodeMirrorHandle])

  override def getDomElement(): L.Element = div(
    cls := "code-mirror-editor-host",
    child <-- config.signal.map(_.showFlowchartGutter).distinct.map { showFlowchart =>
      div(
        cls := ("code-editor code-mirror-editor" + (if showFlowchart && language == Python then " code-mirror-editor-flowchart" else "")),
        styleAttr <-- config.signal.map(_.editorFont).map(font =>
          s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"),
        textArea(cls := "code-mirror-editor__fallback", hidden <-- handle.signal.map(_.nonEmpty),
          value <-- underlyingVar.signal, onInput.mapToValue --> underlyingVar.writer),
        onMountCallback { ctx =>
          val lifetime = new Subscription(ctx.owner, () => ())
          CodeMirror.preload().flatMap(_ => CodeMirror.inBackground(())).foreach { _ =>
            if !lifetime.isKilled && ctx.thisNode.ref.isConnected then {
              val analysis = Option.when(showFlowchart && language == Python)(
                new CodeMirrorFlowchartGutter.Analysis(new CodeMirrorFlowchartGutter.Cache))
              // The callback is required by the native CodeMirror API; Scala consumers observe the Var.
              val created = CodeMirror.editor(EditorConfig(ctx.thisNode.ref, underlyingVar.now(),
                value => if !lifetime.isKilled && underlyingVar.now() != value then underlyingVar.set(value),
                languageToJs(language), analysis.map(_.chartForDoc))).createInstance()
              created.foreach { native =>
                if lifetime.isKilled || !ctx.thisNode.ref.isConnected then {
                  native.destroy()
                  analysis.foreach(_.close())
                } else {
                  new Subscription(ctx.owner, () => {
                    native.destroy()
                    analysis.foreach(_.close())
                    handle.update(_.filterNot(_ == native))
                  })
                  handle.set(Some(native))
                  underlyingVar.signal.foreach(value => if native.getDoc() != value then native.setDoc(value))(using ctx.owner)
                  diagnostics.foreach(values => native.setDiagnostics(js.Array(values.map(_.toJs)*)))(using ctx.owner)
                }
              }
              created.failed.foreach(_ => analysis.foreach(_.close()))
            }
          }
        }
      )
    }
  )
}
object CodeMirrorEditor {
  def languageToJs(language: ProgrammingLanguage): String = language.asJsLanguage.getOrElse("python")


  def apply(logger: Logger, state: Var[String], language: ProgrammingLanguage): CodeMirrorEditor =
    CodeMirrorEditor(logger, state, language, Var(CodeMirrorEditorConfig.Default))

  def pythonEditorForEva(logger: Logger, config: CodeMirrorEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = Python
    override val label = "Python"
    override def create(state: Var[ProgrammingState], appearance: it.evadid.homepage.webElements.editor.code.EditorAppearance): it.evadid.homepage.webElements.HtmlAppElement = {
      val text = state.bimap {
        case ProgrammingStatePythonString(code) => code
        case _ => ""
      }(ProgrammingStatePythonString(_))
      CodeMirrorEditor(logger, text, Python, Var(config))
    }
  }

  final case class Diagnostic(
                               line: Int,
                               endLine: Option[Int] = None,
                               fromCh: Option[Int] = None,
                               toCh: Option[Int] = None,
                               message: String = "",
                               severity: String = "warning"
                             ) derives ReadWriter {
    def toJs: js.Object =
      js.Dynamic.literal(
        line = line,
        endLine = endLine.getOrElse(line),
        fromCh = fromCh.fold[js.Any](js.undefined)(identity),
        toCh = toCh.fold[js.Any](js.undefined)(identity),
        message = message,
        severity = severity
      ).asInstanceOf[js.Object]
  }

}
