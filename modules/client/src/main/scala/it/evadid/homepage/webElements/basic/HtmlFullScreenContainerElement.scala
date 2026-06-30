package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent

case class HtmlFullScreenContainerElement() extends HtmlAppElement {

  private val overlayActiveVar: Var[Boolean] = Var(false)

  private val bodyActiveClass: String = "fullscreen-active"

  private var contentContainer: L.Element = L.div(
    cls := "fullscreen-element-slot"
  )

  private var activeElement: Option[Element] = None
  private var activeAppElement: Option[HtmlAppElement] = None

  private def onKeyDown(event: KeyboardEvent): Unit =
    if event.key == "Escape" && overlayActiveVar.now() then
      event.preventDefault()
      closeFullscreen()
  //else      println("event key: " + event.key)

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
      cls("is-visible") <-- overlayActiveVar.signal,
      L.onKeyDown --> (event => onKeyDown(event)),
      div(
        cls := "fullscreen-content",
        contentContainer
      ),
      button(
        typ := "button",
        cls := "fullscreen-close-button",
        aria.label := "Exit full screen",
        title := "Exit full screen",
        "×",
        onClick --> (_ => closeFullscreen())
      )
    )

  private def withDocumentBody(f: dom.html.Element => Unit): Unit =
    Option(dom.document.body).foreach(f)

  def setElementFullscreen(appElement: HtmlAppElement): Unit = {
    val domElement = appElement.getDomElement()
    if activeElement.contains(domElement) then
      activateOverlay()
    else {
      contentContainer.ref.childNodes.foreach(curNode => contentContainer.ref.removeChild(curNode))
      render(contentContainer.ref, domElement)
      activeElement = Some(domElement)
      activeAppElement = Some(appElement)
      activateOverlay()
    }
  }

  def closeFullscreen(): Unit = {
    activeAppElement.foreach {
      case lifecycle: FullscreenLifecycle => lifecycle.onFullscreenClose()
      case _ =>
    }
    overlayActiveVar.set(false)
    unlockBackground()
    activeAppElement = None
  }

  override def getDomElement(): Element = overlayElement

}
