package it.evadid.homepage.webElements.editor.code.MailEditor

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.scalajs.js
import it.evadid.workbook.elements.interactionElements.emailSimulator.Mail

/** Rebuild an inert HTML fragment. No supplied attributes, URLs or active content reach the document. */
object MailBody {
  private val allowed = Set("P", "DIV", "SPAN", "BR", "STRONG", "B", "EM", "I", "U", "H1", "H2", "H3", "H4", "UL", "OL", "LI", "TABLE", "TBODY", "THEAD", "TR", "TD", "TH", "BLOCKQUOTE", "HR")
  private val discard = Set("SCRIPT", "STYLE", "IFRAME", "OBJECT", "EMBED", "SVG", "MATH", "FORM", "INPUT", "VIDEO", "AUDIO", "LINK", "META", "BASE")
  def allowedImage(value: String): Boolean = value.toLowerCase.matches("pics/[A-Za-z0-9_.-]+\\.(png|jpg|jpeg|gif|webp)") && !value.contains("..")
  private val imageRoot = "../../resources/workbookresources/phishing/Arbeitsheft_Phishing/E-Mail-Simulation/"
  private def fragment(html: String): dom.DocumentFragment = {
    val template = dom.document.createElement("template").asInstanceOf[js.Dynamic]
    template.innerHTML = html
    template.content.asInstanceOf[dom.DocumentFragment]
  }
  def plainText(mail: Mail): String = if (mail.bodyHtml) fragment(mail.body).textContent else mail.body
  def render(mail: Mail, hover: Var[String], notice: Var[String]): Element = div(
    cls := "mail-body", cls.toggle("mail-body--plain") := !mail.bodyHtml,
    onMountCallback { ctx =>
      if (!mail.bodyHtml) ctx.thisNode.ref.textContent = mail.body
      else {
        def append(source: dom.Node, target: dom.Node): Unit = {
          if (source.nodeType == 3) target.appendChild(dom.document.createTextNode(source.textContent))
          else if (source.nodeType == 1) {
            val original = source.asInstanceOf[dom.Element]
            val tag = original.tagName.toUpperCase
            if (!discard.contains(tag)) {
              val clean: dom.Element = if (tag == "A") {
                val b = dom.document.createElement("button").asInstanceOf[dom.html.Button]
                b.`type` = "button"; b.className = "mail-link"
                val url = Option(original.getAttribute("href")).getOrElse("")
                b.title = url
                b.onmouseenter = _ => hover.set(url)
                b.onmouseleave = _ => hover.set("")
                b.onfocus = _ => hover.set(url)
                b.onblur = _ => hover.set("")
                b.onclick = _ => { hover.set(url); notice.set("linkSimulated") }
                b
              } else if (tag == "IMG") {
                val src = Option(original.getAttribute("src")).getOrElse("")
                if (allowedImage(src)) {
                  val img = dom.document.createElement("img").asInstanceOf[dom.html.Image]
                  img.src = imageRoot + src; img.alt = Option(original.getAttribute("alt")).getOrElse("")
                  img
                } else {
                  val replacement = dom.document.createElement("span")
                  replacement.textContent = Option(original.getAttribute("alt")).getOrElse("")
                  replacement
                }
              } else dom.document.createElement(if (allowed.contains(tag)) tag.toLowerCase else "span")
              // Only a small set of presentation hints from the original teaching examples.
              val style = Option(original.getAttribute("style")).getOrElse("").toLowerCase
              if (style.contains("text-align: center") || style.contains("text-align:center")) clean.classList.add("mail-body--center")
              if (style.contains("font-size:10pt") || style.contains("font-size: 10pt")) clean.classList.add("mail-body--small")
              if (style.contains("background")) clean.classList.add("mail-body--panel")
              var i = 0
              while (i < source.childNodes.length) { append(source.childNodes(i), clean); i += 1 }
              target.appendChild(clean)
            }
          }
        }
        val inert = fragment(mail.body)
        var i = 0
        while (i < inert.childNodes.length) { append(inert.childNodes(i), ctx.thisNode.ref); i += 1 }
      }
    }
  )
}
