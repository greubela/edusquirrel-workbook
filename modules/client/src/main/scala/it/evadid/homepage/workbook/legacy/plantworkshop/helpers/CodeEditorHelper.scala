package it.evadid.homepage.workbook.legacy.plantworkshop.helpers

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.timers.*

/** Code editor with TODO highlighting. */
object CodeEditorHelper {
  private var todoHighlightInstalled: Boolean = false
  private val todoPattern = "([A-Za-z_]*TODO[A-Za-z0-9_]*)".r

  private def markTodoTextNodes(root: dom.Element): Unit = {
    val walker = dom.document.createTreeWalker(
      root,
      dom.NodeFilter.SHOW_TEXT,
      null.asInstanceOf[dom.NodeFilter],
      false
    )
    var node = walker.nextNode()
    var textNodes = List.empty[dom.Text]

    while (node != null) {
      textNodes = node.asInstanceOf[dom.Text] :: textNodes
      node = walker.nextNode()
    }

    textNodes.reverse.foreach { txtNode =>
      val value = Option(txtNode.data).getOrElse("")
      if (value.contains("TODO")) {
        val parent = txtNode.parentNode
        if (parent != null) {
          val frag = dom.document.createDocumentFragment()
          var lastIdx = 0

          todoPattern.findAllMatchIn(value).foreach { m =>
            if (m.start > lastIdx) {
              frag.appendChild(dom.document.createTextNode(value.substring(lastIdx, m.start)))
            }

            val span = dom.document.createElement("span")
            span.setAttribute("class", "todo-token-inline")
            span.textContent = m.matched
            frag.appendChild(span)
            lastIdx = m.end
          }

          if (lastIdx < value.length) {
            frag.appendChild(dom.document.createTextNode(value.substring(lastIdx)))
          }

          parent.replaceChild(frag, txtNode)
        }
      }
    }
  }

  private def installTodoHighlighting(): Unit = {
    if (todoHighlightInstalled) return

    if (dom.document.getElementById("todo-token-style") == null) {
      val styleEl = dom.document.createElement("style")
      styleEl.id = "todo-token-style"
      styleEl.textContent = ".editor-container .token.todo-token, .editor-container .todo-token-inline { color: #c62828 !important; font-weight: 700; }"
      dom.document.head.appendChild(styleEl)
    }

    todoHighlightInstalled = true
  }

  def createCodeEditor(
    codeState: Var[String],
    title: String,
    validator: String => String
  ): HtmlElement = {
    var editorInstance: Option[CodeJar] = None

    div(
      h4(title),

      div(
        className := "info-box small-info",
        span(color := "#c62828", fontWeight := "700", "🟥 TODO"),
        span(" = diese Stellen müsst ihr selbst ausfüllen.")
      ),

      div(
        className := "editor-container language-cpp",

        onMountUnmountCallback(
          mount = { nodeCtx =>
            val element = nodeCtx.thisNode.ref.asInstanceOf[dom.Element]

            installTodoHighlighting()

            val highlightFn: js.Function1[dom.Element, Unit] = { el =>
              Prism.highlightElement(el)

              val tokens = el.querySelectorAll("span.token")
              var i = 0
              while (i < tokens.length) {
                val token = tokens.item(i).asInstanceOf[dom.Element]
                if (token.textContent != null && token.textContent.contains("TODO")) {
                  token.classList.add("todo-token")
                }
                i += 1
              }

              markTodoTextNodes(el)
            }

            val jar = new CodeJar(element, highlightFn)
            jar.updateCode(codeState.now())
            jar.onUpdate((code: String) => codeState.set(code))

            editorInstance = Some(jar)
          },
          unmount = { _ =>
            editorInstance.foreach(_.destroy())
            editorInstance = None
          }
        )
      ),

      button(
        "Code prüfen",
        className := "btn-check",
        onClick --> { _ =>
          val code = codeState.now()
          val result = validator(code)
          dom.window.alert(result)
        }
      )
    )
  }
}
