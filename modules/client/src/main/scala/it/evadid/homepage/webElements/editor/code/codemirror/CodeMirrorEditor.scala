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
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

case class CodeMirrorEditor(
                             logger: Logger,
                             underlyingVar: Var[String],
                             handleFunctionInput: String => Unit = _ => {},
                             language: ProgrammingLanguage,
                             config: Var[CodeMirrorEditorConfig]
                           ) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {

  import CodeMirrorEditor.*

  private val handle = Var(Option.empty[CodeMirrorHandle])
  private val pendingDiagnostics = Var(Option.empty[(String, Seq[Diagnostic])])
  private val useTextareaFallback: Var[Boolean] = Var(true)
  private val flowchartCache = new CodeMirrorFlowchartGutter.Cache

  def focus(): Unit = handle.now().foreach(_.focus())

  def currentDoc: Option[String] = handle.now().map(_.getDoc())

  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit =
    pendingDiagnostics.set(Some(underlyingVar.now() -> diagnostics))
    handle.now().foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs) *)))

  def clearDiagnostics(): Unit = setDiagnostics(Nil)

  override def getDomElement(): L.Element = div(
    cls := "code-mirror-editor-host",
    child <-- config.signal.map(_.showFlowchartGutter).distinct.map(renderEditor)
  )

  private def renderEditor(showFlowchart: Boolean): L.Element = {
    val mountedToken = Var(Option.empty[AnyRef])
    val mountedHandle = Var(Option.empty[CodeMirrorHandle])
    val mountedAnalysis = Var(Option.empty[CodeMirrorFlowchartGutter.Analysis])
    div(
      cls := ("code-editor code-mirror-editor" + (if (showFlowchart && language == Python) " code-mirror-editor-flowchart" else "")),
      styleAttr <-- config.signal.map(_.editorFont).map(font =>
        s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"
      ),
      textArea(
        cls := "code-mirror-editor__fallback",
        hidden <-- useTextareaFallback.signal.map(show => !show),
        value <-- underlyingVar.signal,
        onInput.mapToValue --> { value =>
          if mountedToken.now().nonEmpty then
            underlyingVar.writer.onNext(value)
            handleFunctionInput(value)
        }
      ),
      onMountCallback { ctx =>
        val token = new Object
        mountedToken.set(Some(token))
        useTextareaFallback.set(true)
        def isActive: Boolean =
          mountedToken.now().contains(token) && ctx.thisNode.ref.isConnected
        CodeMirrorEditorLoading.load().flatMap(facade => CodeMirrorEditorLoading.inBackground(Some(facade))).recover { case scala.util.control.NonFatal(_) => None }.foreach {
          case Some(cmFacade) if isActive =>
            val container = ctx.thisNode.ref
            val initialValue = underlyingVar.now()

            val analysis = Option.when(showFlowchart && language == Python)(new CodeMirrorFlowchartGutter.Analysis(flowchartCache))
            mountedAnalysis.set(analysis)

            val createdHandle = cmFacade.createEditor(
              EditorConfig(
                parent = container,
                doc = initialValue,
                language = language.asJsLanguage.getOrElse("python"),
                flowchart = analysis.map(_.chartForDoc),
                onDocChange = value =>
                  if (isActive && underlyingVar.now() != value) {
                    pendingDiagnostics.set(None)
                    underlyingVar.writer.onNext(value)
                    handleFunctionInput(value)
                  }
              )
            )

            if !isActive then {
              createdHandle.destroy()
              analysis.foreach(_.close())
            }
            else {
              mountedHandle.set(Some(createdHandle))
              handle.set(Some(createdHandle))
              useTextareaFallback.set(false)
              pendingDiagnostics.now().filter(_._1 == initialValue).foreach { case (_, diagnostics) =>
                createdHandle.setDiagnostics(js.Array(diagnostics.map(_.toJs) *))
              }

              underlyingVar.signal.foreach { value =>
                if (isActive && createdHandle.getDoc() != value) {
                  pendingDiagnostics.set(None)
                  createdHandle.setDoc(value)
                }
              }(using ctx.owner)
            }

          case None if isActive =>
            mountedAnalysis.now().foreach(_.close())
            mountedAnalysis.set(None)
            useTextareaFallback.set(true)
          case _ => ()
        }
      },
      onUnmountCallback { _ =>
        val retiringHandle = mountedHandle.now()
        mountedToken.set(None)
        // A replacement can mount in the same transaction. Evaluate handle ownership
        // when the queued update commits, so old cleanup cannot clear the replacement.
        handle.update(_.filterNot(retiringHandle.contains))
        retiringHandle.foreach(_.destroy())
        mountedHandle.set(None)
        mountedAnalysis.now().foreach(_.close())
        mountedAnalysis.set(None)
      }
    )
  }


}
object CodeMirrorEditor {
  def languageToJs(language: ProgrammingLanguage): String = language.asJsLanguage.getOrElse("python")


  def apply(logger: Logger, state: Var[String], langauge: ProgrammingLanguage): CodeMirrorEditor = {
     CodeMirrorEditor(logger, state, _ => {}, langauge, Var(CodeMirrorEditorConfig.Default))
  }

  def pythonEditorForEva(tabLogger: Logger, editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
      appearance: Option[it.evadid.homepage.webElements.editor.code.EditorAppearance] = None): EvaEditorTab[String] = {

    val internal: Var[String] = Var[String]("")
    val editor = CodeMirrorEditor(tabLogger, internal, language = Python, config = Var(editorConfig))

    new EvaEditorTab[String]() {
      override val underlyingVar: Var[String] = internal

      override def toProgram(internal: String): ProgrammingState = ProgrammingStatePythonString(internal)

      override def fromProgram(external: ProgrammingState): String = external.toPython.code

      override def getDomElement(): L.Element = editor.getDomElement()

      override val logger: Logger = tabLogger

      override def label: String = "Python"

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
    val parent: dom.Element
    val doc: js.UndefOr[String]
    val language: js.UndefOr[String]
    val onDocChange: js.Function1[String, Unit]
    val flowchart: js.UndefOr[js.Function1[String, js.Promise[js.Object]]]
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
