package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.facades.CodeMirror.{CodeMirrorHandle, EditorConfig}

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.ProgrammingStateView
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.facades.CodeMirror
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorContext, EvaEditorLabels, EvaEditorTab}
import it.evadid.vm.code.errors.ProgrammingConversionError
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
    loadingLabel: Signal[String],
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil),
    createEditor: EditorConfig => scala.concurrent.Future[CodeMirrorHandle] = settings => CodeMirror.editor(settings).createInstance(),
    firstLineNumber: Signal[Int] = Val(1),
    /** Equivalent documents leave the user's text, including formatting the state cannot represent, untouched. */
    sameSource: (String, String) => Boolean = _ == _,
    /** The flowchart gutter of each language, shown when the configuration enables it. */
    flowchartSource: ProgrammingLanguage => Option[CodeMirrorFlowchartSource] = CodeMirrorFlowchartSource.forLanguage
) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {
  import CodeMirrorEditor.*

  private val handle = Var(Option.empty[CodeMirrorHandle])
  private val loadFailed = Var(false)
  private val flowchart = flowchartSource(language)

  override def getDomElement(): L.Element = div(
    cls := "code-mirror-editor-host",
    child <-- config.signal.map(_.showFlowchartGutter).distinct.map { showFlowchart =>
      div(
        cls := ("code-editor code-mirror-editor" + (if showFlowchart && flowchart.nonEmpty then " code-mirror-editor-flowchart" else "")),
        styleAttr <-- config.signal.map(_.editorFont).map(font =>
          s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"),
        p(cls := "code-mirror-editor__loading", role := "status",
          hidden <-- handle.signal.combineWith(loadFailed.signal).map((ready, failed) => ready.nonEmpty || failed),
          child.text <-- loadingLabel),
        textArea(cls := "code-mirror-editor__fallback", hidden <-- handle.signal.map(_.nonEmpty),
          value <-- underlyingVar.signal, onInput.mapToValue --> underlyingVar.writer),
        onMountCallback { ctx =>
          loadFailed.set(false)
          val lifetime = new Subscription(ctx.owner, () => ())
          val lineNumbers = firstLineNumber.map(value => math.max(1, value)).distinct.observe(using ctx.owner)
          CodeMirror.inBackground(()).foreach { _ =>
            if !lifetime.isKilled && ctx.thisNode.ref.isConnected then {
              val analysis = flowchart.filter(_ => showFlowchart)
              // The callback is required by the native CodeMirror API; Scala consumers observe the Var.
              val created = createEditor(EditorConfig(ctx.thisNode.ref, underlyingVar.now(),
                value => if !lifetime.isKilled && !sameSource(underlyingVar.now(), value) then underlyingVar.set(value),
                languageToJs(language), analysis.map(current => source => current.chart(source).map(CodeMirrorFlowchartGutter.render)),
                firstLineNumber = lineNumbers.now()))
              created.foreach { native =>
                if lifetime.isKilled || !ctx.thisNode.ref.isConnected then native.destroy()
                else {
                  new Subscription(ctx.owner, () => {
                    native.destroy()
                    handle.update(_.filterNot(_ == native))
                  })
                  handle.set(Some(native))
                  underlyingVar.signal.foreach(value => if !sameSource(native.getDoc(), value) then native.setDoc(value))(using ctx.owner)
                  diagnostics.foreach(values => native.setDiagnostics(js.Array(values.map(_.toJs)*)))(using ctx.owner)
                  lineNumbers.foreach(native.setFirstLineNumber)(using ctx.owner)
                }
              }
              created.failed.foreach(_ => if !lifetime.isKilled then loadFailed.set(true))
            }
          }
        }
      )
    }
  )
}
object CodeMirrorEditor {
  def languageToJs(language: ProgrammingLanguage): String = language.asJsLanguage.getOrElse("python")

  def apply(logger: Logger, state: Var[String], language: ProgrammingLanguage, loadingLabel: Signal[String]): CodeMirrorEditor =
    CodeMirrorEditor(logger, state, language, Var(CodeMirrorEditorConfig.Default), loadingLabel)

  def pythonEditorForEva(config: CodeMirrorEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = Python
    override def icon = Some(it.evadid.homepage.webElements.editor.code.EvaEditor.EvaLanguageIcons.python)
    override def create(state: Var[ProgrammingState], context: EvaEditorContext): it.evadid.homepage.webElements.HtmlAppElement = {
      val text = ProgrammingStateView(state, inactive = "") {
        case ProgrammingStateRaw(Python, code) => code
        case value: ProgrammingStatePythonStructured => value.toString
      }(ProgrammingStateRaw(Python, _))
      CodeMirrorEditor(context.logger, text, Python, Var(config), diagnostics = context.conversionDiagnostics,
        loadingLabel = context.loading)
    }
  }

  /** The error's line and column (if known), with `message` as its explanation. */
  def mark(error: ProgrammingConversionError, message: String): Seq[Diagnostic] =
    error.line.toSeq.map(line => Diagnostic(line, fromCh = error.column.map(_ - 1), toCh = error.column, message = message, severity = "error"))

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
