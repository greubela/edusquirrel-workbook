package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.facades.CodeMirror.{CodeMirrorHandle, EditorConfig}

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.facades.CodeMirror
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
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
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil),
    createEditor: EditorConfig => scala.concurrent.Future[CodeMirrorHandle] = settings => CodeMirror.editor(settings).createInstance(),
    loadingLabel: Signal[String] = Val("Loading editor…"),
    firstLineNumber: Signal[Int] = Val(1),
    /** Equivalent documents leave the user's text, including formatting the state cannot represent, untouched. */
    sameSource: (String, String) => Boolean = _ == _
) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {
  import CodeMirrorEditor.*

  private val handle = Var(Option.empty[CodeMirrorHandle])
  private val loadFailed = Var(false)
  /** Typed text not yet in `underlyingVar`: the answer (history, sync, grading) follows after a typing pause,
    * on blur or on unmount, so these reactions never delay a keystroke.
    */
  private val unwrittenText = Var(Option.empty[UnwrittenText])

  /** Writes typed text only onto the answer it was typed over: a restoration, synchronization or language
    * conversion that arrived meanwhile is newer and stays.
    */
  private def writeTypedText(): Unit = unwrittenText.now().foreach { pending =>
    unwrittenText.set(None)
    val current = underlyingVar.now()
    if current == pending.base && !sameSource(current, pending.text) then underlyingVar.set(pending.text)
  }

  override def getDomElement(): L.Element = div(
    cls := "code-mirror-editor-host",
    // blur does not bubble, so it is captured from CodeMirror's content: clicking a button or tab writes the typed text first.
    onBlur.useCapture --> (_ => writeTypedText()),
    child <-- config.signal.map(_.showFlowchartGutter).distinct.map { showFlowchart =>
      div(
        cls := ("code-editor code-mirror-editor" + (if showFlowchart && language == Python then " code-mirror-editor-flowchart" else "")),
        styleAttr <-- config.signal.map(_.editorFont).map(font =>
          s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"),
        p(cls := "code-mirror-editor__loading", role := "status",
          hidden <-- handle.signal.combineWith(loadFailed.signal).map((ready, failed) => ready.nonEmpty || failed),
          child.text <-- loadingLabel),
        textArea(cls := "code-mirror-editor__fallback", hidden <-- handle.signal.map(_.nonEmpty),
          inContext(fallback => underlyingVar.signal --> (answer => if !sameSource(fallback.ref.value, answer) then fallback.ref.value = answer)),
          onInput.mapToValue --> (typed => if !sameSource(underlyingVar.now(), typed) then underlyingVar.set(typed))),
        onMountCallback { ctx =>
          loadFailed.set(false)
          val lifetime = new Subscription(ctx.owner, () => ())
          val lineNumbers = firstLineNumber.map(value => math.max(1, value)).distinct.observe(using ctx.owner)
          CodeMirror.inBackground(()).foreach { _ =>
            if !lifetime.isKilled && ctx.thisNode.ref.isConnected then {
              val analysis = Option.when(showFlowchart && language == Python)(
                new CodeMirrorFlowchartGutter.Analysis(new CodeMirrorFlowchartGutter.Cache))
              // The callback is required by the native CodeMirror API; Scala consumers observe the Var.
              val created = createEditor(EditorConfig(ctx.thisNode.ref, underlyingVar.now(),
                value => if !lifetime.isKilled then
                  unwrittenText.update(pending => Some(UnwrittenText(pending.fold(underlyingVar.now())(_.base), value))),
                languageToJs(language), analysis.map(current => source => current.chartForDoc(source).map(CodeMirrorFlowchartGutter.render)),
                firstLineNumber = lineNumbers.now()))
              created.foreach { native =>
                if lifetime.isKilled || !ctx.thisNode.ref.isConnected then {
                  native.destroy()
                  analysis.foreach(_.close())
                } else {
                  unwrittenText.signal.changes.collect { case Some(_) => () }.debounce(typingWriteDelayMillis)
                    .foreach(_ => writeTypedText())(using ctx.owner)
                  new Subscription(ctx.owner, () => {
                    writeTypedText()
                    native.destroy()
                    analysis.foreach(_.close())
                    handle.update(_.filterNot(_ == native))
                  })
                  handle.set(Some(native))
                  underlyingVar.signal.foreach(value => if !sameSource(native.getDoc(), value) then {
                    unwrittenText.set(None)
                    native.setDoc(value)
                  })(using ctx.owner)
                  diagnostics.foreach(values => native.setDiagnostics(js.Array(values.map(_.toJs)*)))(using ctx.owner)
                  lineNumbers.foreach(native.setFirstLineNumber)(using ctx.owner)
                }
              }
              created.failed.foreach { _ =>
                analysis.foreach(_.close())
                if !lifetime.isKilled then loadFailed.set(true)
              }
            }
          }
        }
      )
    }
  )
}
object CodeMirrorEditor {
  /** Typing pause after which the typed text becomes the bound answer. */
  val typingWriteDelayMillis: Int = 400

  /** Typed `text` and the answer `base` it was typed over. */
  private final case class UnwrittenText(base: String, text: String)

  def languageToJs(language: ProgrammingLanguage): String = language.asJsLanguage.getOrElse("python")

  def apply(logger: Logger, state: Var[String], language: ProgrammingLanguage): CodeMirrorEditor =
    CodeMirrorEditor(logger, state, language, Var(CodeMirrorEditorConfig.Default))

  def pythonEditorForEva(logger: Logger, config: CodeMirrorEditorConfig, tabLabel: String = "Python"): EvaEditorTab = new EvaEditorTab {
    override val language = Python
    override val label = tabLabel
    override def create(state: Var[ProgrammingState], appearance: it.evadid.homepage.webElements.editor.code.EditorAppearance,
        marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): it.evadid.homepage.webElements.HtmlAppElement = {
      val text = state.bimap {
        case ProgrammingStateRaw(Python, code) => code
        case value: ProgrammingStatePythonStructured => value.toString
        case _ => ""
      }(ProgrammingStatePythonStructured(_))
      // The structured state orders classes first and completes empty blocks; typed text it represents stays as typed.
      CodeMirrorEditor(logger, text, Python, Var(config), diagnostics = CodeMirrorEditor.marks(marker), loadingLabel = loading,
        sameSource = (left, right) => left == right || ProgrammingStatePythonStructured(left) == ProgrammingStatePythonStructured(right))
    }
  }

  def marks(marker: Signal[Option[ProgrammingConversionError]]): Signal[Seq[Diagnostic]] = marker.map {
    case Some(error) =>
      error.line.toSeq.map(line => Diagnostic(line, fromCh = error.column.map(_ - 1),
        toCh = error.column, message = "", severity = "error"))
    case None => Nil
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
