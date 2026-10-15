package it.evadid.homepage.webElements.editor.code.codemirror

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.evacuation.eva2.control.EvaControlModeWithSubmodes
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorTab, *}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor.{CodeMirrorHandle, Diagnostic, EditorConfig}
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, *}
import org.scalajs.dom
import org.scalajs.dom.{HTMLDivElement, SVGSVGElement}
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

case class CodeMirrorEditor(
                             logger: Logger,
                             underlyingVar: Var[String],
                             handleFunctionInput: String => Unit = _ => {},
                             language: ProgrammingLanguage,
                             config: Var[CodeMirrorEditorConfig]
                           ) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {

  given ExecutionContext = ExecutionContext.global

  private val gutterHandler = CodeMirrorFlowchartGutter(logger, WebWorkerExecutionClient(CodeMirrorEditorLoading.workerUrl, timeoutMilliseconds = 30000))

  private val lastDiagnostics = Var(Option[Diagnostic]) //.empty[(String, Seq[Diagnostic])]
  private val handleVar: Var[Option[CodeMirrorHandle]] = Var(None)


  private def onValueChanged(nextValue: String): Unit = {
    if (underlyingVar.now() != nextValue) {
      underlyingVar.set(nextValue)
    }
    handleVar.now().foreach(handle => {
      if (handle.getDoc() != nextValue) {
        handle.setDoc(nextValue)
      }
      handle.focus()
      //   handle.setDiagnostics(js.Array(diagnostics.map(_.toJs) *))
    })
  }

  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit = {
   // pendingDiagnostics = Some(content.now() -> diagnostics)
    handleVar.now().foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs) *)))
  }

  def createNewTextArea(): TextArea = textArea(
    cls := "code-mirror-editor__fallback",
    hidden <-- handleVar.signal.map(_.nonEmpty),
    value <-- underlyingVar.signal,
    onInput.mapToValue --> onValueChanged
  )

  def createNewCodemirrorHandle(container: HTMLDivElement, initValue: String): Future[CodeMirrorHandle] = {
    val editorConfig = EditorConfig(
      parent = container,
      doc = initValue,
      language = language.asJsLanguage.getOrElse("python"),
      flowchart = Some(gutterHandler.loadAsJsPromise),
      onDocChange = onValueChanged
    )
    val res: Future[CodeMirrorHandle] = CodeMirrorEditorLoading.load().map(_.createEditor(editorConfig))
    res
  }

  //def currentDoc: Option[String] = handle.map(_.getDoc())

  /* def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit = Future {
     pendingDiagnostics = Some(contentAsString.now() -> diagnostics)
     handle.foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs) *)))
   }()*/


  override def getDomElement(): L.Element = {

    div(
      cls <-- config.signal.map(curConfig => if (!curConfig.showFlowchartGutter) "code-editor code-mirror-editor" else "code-editor code-mirror-editor code-mirror-editor-flowchart"),
      styleAttr <-- config.signal.map(_.editorFont).map(font =>
        s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"
      ),
      createNewTextArea(),

      onMountCallback { ctx =>
        createNewCodemirrorHandle(ctx.thisNode.ref, underlyingVar.now())
      }
    )
  }

}

object CodeMirrorEditor {

  def apply(logger: Logger, state: Var[String], langauge: ProgrammingLanguage): CodeMirrorEditor = {
     CodeMirrorEditor(logger, state, _ => {}, langauge, Var(CodeMirrorEditorConfig.Default))
  }

  def pythonEditorForEva(logger: Logger): EvaEditorTab[String] = {

    val internal: Var[String] = Var[String]("")
    val editor = CodeMirrorEditor(logger, internal, Python)

    new EvaEditorTab[String]() {
      override val underlyingVar: Var[String] = internal

      override def convert(internal: String): ProgrammingState = ProgrammingStatePythonString(internal)

      override def convert(external: ProgrammingState): String = external.toPython.code

      override def getDomElement(): L.Element = editor.getDomElement()

      override def logger: Logger = logger

      override def label: String = "Python Editor"

      override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = None

      override def additionalCssForEvaEditor: String = ""
    }

  }


  @js.native
  private[code] trait CodeMirrorFacade extends js.Object {
    def createEditor(config: EditorConfig): CodeMirrorHandle = js.native
  }

  @js.native
  trait CodeMirrorHandle extends js.Object {
    def setDoc(value: String): Unit = js.native

    def getDoc(): String = js.native

    def setDiagnostics(diagnostics: js.Array[js.Object]): Unit = js.native

    def focus(): Unit = js.native

    def destroy(): Unit = js.native
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

  trait EditorConfig extends js.Object {
    var parent: dom.Element
    var doc: js.UndefOr[String]
    var language: js.UndefOr[String]
    var onDocChange: js.Function1[String, Unit]
    var flowchart: js.UndefOr[js.Function1[String, js.Promise[js.Object]]]
  }

  object EditorConfig {
    def apply(
               parent: dom.Element,
               doc: String,
               onDocChange: String => Unit,
               language: String = "python",
               flowchart: Option[String => js.Promise[js.Object]] = None
             ): EditorConfig = {
      js.Dynamic.literal(
        parent = parent,
        doc = doc,
        language = language,
        onDocChange = (value: String) => onDocChange(value),
        flowchart = flowchart.fold[js.UndefOr[js.Function1[String, js.Promise[js.Object]]]](js.undefined)(
          callback => ((value: String) => callback(value)): js.Function1[String, js.Promise[js.Object]]
        )
      ).asInstanceOf[EditorConfig]
    }
  }


}
