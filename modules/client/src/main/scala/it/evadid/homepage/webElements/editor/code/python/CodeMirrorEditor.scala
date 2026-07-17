package it.evadid.homepage.webElements.editor.code.python

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import org.scalajs.dom
import todomove.datastructures.web.font.AppFont

import scala.scalajs.js

case class CodeMirrorEditor(
                             content: Var[String],
                             onUserInput: String => Unit = _ => (),
                             editorFont: Signal[AppFont] = Val(AppFont("JetBrains Mono", 14))
                           ) extends HtmlAppElement {

  import CodeMirrorEditor.*

  private var handle: Option[CodeMirrorHandle] = None
  private var updatingFromEditor: Boolean = false

  def focus(): Unit = handle.foreach(_.focus())

  def currentDoc: Option[String] = handle.map(_.getDoc())

  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit =
    handle.foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs) *)))

  def clearDiagnostics(): Unit = setDiagnostics(Nil)

  override def getDomElement(): L.Element = {
    div(
      cls := "code-mirror-editor",
      styleAttr <-- editorFont.map(font =>
        s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"
      ),
      onMountCallback { ctx =>
        waitForFacade {
          case Some(cmFacade) =>
            val container = ctx.thisNode.ref
            val initialValue = content.now()

            var updatingFromVar = false

            val createdHandle = cmFacade.createEditor(
              EditorConfig(
                parent = container,
                doc = initialValue,
                onDocChange = value =>
                  if (!updatingFromVar) {
                    updatingFromEditor = true
                    content.writer.onNext(value)
                    onUserInput(value)
                    updatingFromEditor = false
                  }
              )
            )

            handle = Some(createdHandle)

            content.signal.foreach { value =>
              handle.foreach { editorHandle =>
                if (!updatingFromEditor && editorHandle.getDoc() != value) {
                  updatingFromVar = true
                  editorHandle.setDoc(value)
                  updatingFromVar = false
                }
              }
            }(using ctx.owner)

          case None =>
            dom.console.error("CodeMirror facade is not available on window.EduSquirrelCodeMirror")
        }
      },
      onUnmountCallback { _ =>
        handle.foreach(_.destroy())
        handle = None
      }
    )
  }
}

object CodeMirrorEditor {

  @js.native
  private trait CodeMirrorFacade extends js.Object {
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
                             ) {
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
    var doc: String
    var onDocChange: js.Function1[String, Unit]
  }

  object EditorConfig {
    def apply(parent: dom.Element, doc: String, onDocChange: String => Unit): EditorConfig = {
      js.Dynamic.literal(
        parent = parent,
        doc = doc,
        onDocChange = (value: String) => onDocChange(value)
      ).asInstanceOf[EditorConfig]
    }
  }

  private def facade: Option[CodeMirrorFacade] = {
    val maybeFacade = js.Dynamic.global.selectDynamic("EduSquirrelCodeMirror")
    if (js.isUndefined(maybeFacade) || maybeFacade == null) None
    else Some(maybeFacade.asInstanceOf[CodeMirrorFacade])
  }

  private def waitForFacade(callback: Option[CodeMirrorFacade] => Unit): Unit = {
    facade match {
      case some@Some(_) =>
        callback(some)
      case None =>
        val readyPromise = js.Dynamic.global.selectDynamic("EduSquirrelCodeMirrorReady")
        if (js.isUndefined(readyPromise) || readyPromise == null) {
          callback(None)
        } else {
          readyPromise
            .asInstanceOf[js.Promise[js.Any]]
            .`then`[Unit]((_: js.Any) => callback(facade))
            .`catch`(((error: scala.Any) => {
              dom.console.error("Failed to initialize CodeMirror facade", error.asInstanceOf[js.Any])
              callback(None)
              (): Unit
            }): js.Function1[scala.Any, Unit | js.Thenable[Unit]])
        }
    }
  }
}
