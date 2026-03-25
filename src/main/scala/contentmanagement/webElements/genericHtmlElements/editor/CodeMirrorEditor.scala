package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.editor.CodeMirrorEditor.{CodeMirrorHandle, EditorConfig, waitForFacade}
import org.scalajs.dom

import scala.scalajs.js

case class CodeMirrorEditor(
                             content: Var[String],
                             onUserInput: String => Unit = _ => ()
                           ) extends HtmlAppElement {

  private var handle: Option[CodeMirrorHandle] = None
  private var updatingFromEditor: Boolean = false

  def focus(): Unit = handle.foreach(_.focus())

  def currentDoc: Option[String] = handle.map(_.getDoc())

  override def getDomElement(): L.Element = {
    div(
      cls := "code-mirror-editor",
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
            }(ctx.owner)

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

    def focus(): Unit = js.native

    def destroy(): Unit = js.native
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
