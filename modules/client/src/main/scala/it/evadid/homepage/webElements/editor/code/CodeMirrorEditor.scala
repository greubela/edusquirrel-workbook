package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import org.scalajs.dom
import it.evadid.core.datastructures.font.AppFont

import scala.scalajs.js

case class CodeMirrorEditor(
                             content: Var[String],
                             onUserInput: String => Unit = _ => (),
                             editorFont: Signal[AppFont] = Val(AppFont("JetBrains Mono", 14)),
                             language: ProgrammingLanguage = Python,
                             editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default
                           ) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {

  import CodeMirrorEditor.*

  private class EditorMount(val container: dom.Element) {
    var handle: Option[CodeMirrorHandle] = None
    var analysis: Option[CodeMirrorEditorFlowchart.Analysis] = None
  }
  private var mounts = List.empty[EditorMount]
  private var pendingDiagnostics = Option.empty[(String, Seq[Diagnostic])]
  private val flowchartCache = Option.when(editorConfig.showFlowchartGutter && language == Python)(new CodeMirrorEditorFlowchart.Cache)

  private def liveHandles: List[CodeMirrorHandle] =
    mounts.filter(_.container.isConnected).flatMap(_.handle)

  def focus(): Unit = liveHandles.headOption.foreach(_.focus())

  def currentDoc: Option[String] = liveHandles.headOption.map(_.getDoc())

  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit =
    pendingDiagnostics = Some(content.now() -> diagnostics)
    liveHandles.foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs) *)))

  def clearDiagnostics(): Unit = setDiagnostics(Nil)

  override def getDomElement(): L.Element = {
    var mounted = Option.empty[EditorMount]
    var updatingFromEditor = false
    val useTextareaFallback = Var(true)
    div(
      cls := ("code-editor code-mirror-editor" + (if (flowchartCache.nonEmpty) " code-mirror-editor-flowchart" else "")),
      styleAttr <-- editorFont.map(font =>
        s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"
      ),
      textArea(
        cls := "code-mirror-editor__fallback",
        hidden <-- useTextareaFallback.signal.map(show => !show),
        value <-- content.signal,
        onInput.mapToValue --> { value =>
          if mounted.exists(_.container.isConnected) then
            content.writer.onNext(value)
            onUserInput(value)
        }
      ),
      onMountCallback { ctx =>
        val mount = new EditorMount(ctx.thisNode.ref)
        mounted = Some(mount)
        mounts = mount :: mounts
        useTextareaFallback.set(true)
        def isActive: Boolean =
          mounted.contains(mount) && mount.container.isConnected
        waitForFacade {
          case Some(cmFacade) if isActive =>
            val container = ctx.thisNode.ref
            val initialValue = content.now()

            var updatingFromVar = false
            val analysis = flowchartCache.map(new CodeMirrorEditorFlowchart.Analysis(_))
            mount.analysis = analysis

            val createdHandle = cmFacade.createEditor(
              EditorConfig(
                parent = container,
                doc = initialValue,
                language = languageToJs(language),
                flowchart = analysis.map(_.chartForDoc),
                onDocChange = value =>
                  if (isActive && !updatingFromVar) {
                    pendingDiagnostics = None
                    updatingFromEditor = true
                    try {
                      content.writer.onNext(value)
                      onUserInput(value)
                    } finally updatingFromEditor = false
                  }
              )
            )

            if !isActive then {
              createdHandle.destroy()
              analysis.foreach(_.close())
            }
            else {
              mount.handle = Some(createdHandle)
              useTextareaFallback.set(false)
              pendingDiagnostics.filter(_._1 == initialValue).foreach { case (_, diagnostics) =>
                createdHandle.setDiagnostics(js.Array(diagnostics.map(_.toJs) *))
              }

              content.signal.foreach { value =>
                if (isActive && !updatingFromEditor && createdHandle.getDoc() != value) {
                  pendingDiagnostics = None
                  updatingFromVar = true
                  try createdHandle.setDoc(value)
                  finally updatingFromVar = false
                }
              }(using ctx.owner)
            }

          case None if isActive =>
            mount.analysis.foreach(_.close())
            mount.analysis = None
            useTextareaFallback.set(true)
          case _ => ()
        }
      },
      onUnmountCallback { _ =>
        val previous = mounted
        mounted = None
        previous.foreach { mount =>
          mounts = mounts.filterNot(_ == mount)
          mount.handle.foreach(_.destroy())
          mount.handle = None
          mount.analysis.foreach(_.close())
          mount.analysis = None
        }
        if mounts.isEmpty then pendingDiagnostics = None
      }
    )
  }

  override def underlyingVar: Var[String] = content

  override def config: Val[CodeMirrorEditorConfig] = Val(editorConfig)
}

object CodeMirrorEditor {

  def languageToJs(language: ProgrammingLanguage): String =
    language match {
      case AppLanguage.Cpp => "cpp"
      case AppLanguage.C => "c"
      case AppLanguage.Python => "python"
      case AppLanguage.Java => "java"
      case AppLanguage.SQL => "sql"
      case _ => "python"
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
                             ) derives upickle.default.ReadWriter {
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

  private def waitForFacade(callback: Option[CodeMirrorFacade] => Unit): Unit = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    CodeMirrorEditorLoading.load().flatMap { facade =>
      CodeMirrorEditorLoading.inBackground(callback(Some(facade)))
    }.recover { case error =>
      dom.console.warn("CodeMirror is unavailable; retaining the textarea editor", error.getMessage)
      callback(None)
    }
  }
}
