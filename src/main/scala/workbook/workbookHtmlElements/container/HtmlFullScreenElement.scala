package workbook.workbookHtmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, html}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlFullScreenElement() extends HtmlWorkbookElement {

  private val overlayActiveVar: Var[Boolean] = Var(false)

  private val bodyActiveClass: String = "fullscreen-active"

  private var contentContainer: L.Element = L.div(
    cls := "fullscreen-element-slot"
  )

  private var activeElement: Option[Element] = None

  private def onKeyDown(event: KeyboardEvent): Unit =
    if event.key == "Escape" && overlayActiveVar.now() then
      event.preventDefault()
      closeFullscreen()
    else
      println("event key: " + event.key)

  private def lockBackground(): Unit =
    withDocumentBody(_.classList.add(bodyActiveClass))

  private def unlockBackground(): Unit =
    withDocumentBody(_.classList.remove(bodyActiveClass))

  private def activateOverlay(): Unit =
    overlayActiveVar.set(true)
    lockBackground()


  private val overlayElement: Element =
    div(
      cls := "fullscreen-overlay",
      cls.toggle("is-visible") <-- overlayActiveVar.signal,
      L.onKeyDown --> (event => onKeyDown(event)),
      button(
        typ := "button",
        cls := "fullscreen-close-button",
        aria.label := "Exit full screen",
        title := "Exit full screen",
        "×",
        onClick --> (_ => closeFullscreen())
      ),
      div(
        cls := "fullscreen-content",
        contentContainer
      )
    )

  private def withDocumentBody(f: dom.html.Element => Unit): Unit =
    Option(dom.document.body).foreach(f)

  def setElementFullscreen(domElement: Element): Unit =
    if activeElement.contains(domElement) then
      activateOverlay()
    else {
      contentContainer.ref.childNodes.foreach(curNode => contentContainer.ref.removeChild(curNode))
      render(contentContainer.ref, domElement)
      activeElement = Some(domElement)
      activateOverlay()
    }

  def closeFullscreen(): Unit = {
    overlayActiveVar.set(false)
    unlockBackground()
  }

  override def getDomElement(): Element = overlayElement

}
