package workbook.workbookHtmlElements.container

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import org.scalajs.dom.html
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlFullScreenElement() extends HtmlWorkbookElement {

  private val overlayActiveVar: Var[Boolean] = Var(false)

  private val bodyActiveClass: String = "fullscreen-active"
  private val placeholderClass: String = "fullscreen-placeholder"

  private var contentContainer: Option[html.Element] = None
  private var activeElement: Option[Element] = None
  private var placeholderNode: Option[dom.Node] = None

  private val overlayElement: Element =
    div(
      cls := "fullscreen-overlay",
      cls.toggle("is-visible") <-- overlayActiveVar.signal,
      documentEvents.onKeyDown --> (event =>
        if event.key == "Escape" && overlayActiveVar.now() then
          event.preventDefault()
          clearFullscreen()
      ),
      button(
        typ := "button",
        cls := "fullscreen-close-button",
        aria.label := "Exit full screen",
        title := "Exit full screen",
        "×",
        onClick --> (_ => clearFullscreen())
      ),
      div(
        cls := "fullscreen-content",
        div(
          cls := "fullscreen-element-slot",
          onMountCallback(ctx => contentContainer = Some(ctx.thisNode.ref))
        )
      )
    )

  private def withDocumentBody(f: dom.html.Element => Unit): Unit =
    Option(dom.document.body).foreach(f)

  def setElementFullscreen(domElement: Element): Unit =
    if activeElement.contains(domElement) then
      overlayActiveVar.set(true)
      withDocumentBody(_.classList.add(bodyActiveClass))
    else
      contentContainer match
        case None =>
          dom.console.error("Fullscreen container is not ready yet.")
        case Some(container) =>
          clearFullscreen()

          val targetNode = domElement.ref
          val parentNode = targetNode.parentNode

          if parentNode == null then
            dom.console.error("Element must be attached to the DOM before entering full screen mode.")
          else
            val placeholder = dom.document.createElement("div")
            placeholder.classList.add(placeholderClass)
            placeholder.setAttribute("aria-hidden", "true")
            parentNode.insertBefore(placeholder, targetNode)

            container.appendChild(targetNode)

            placeholderNode = Some(placeholder)
            activeElement = Some(domElement)
            overlayActiveVar.set(true)
            withDocumentBody(_.classList.add(bodyActiveClass))

  def clearFullscreen(): Unit =
    (activeElement, placeholderNode) match
      case (Some(element), Some(placeholder)) =>
        val targetNode = element.ref
        val placeholderParent = placeholder.parentNode

        if placeholderParent != null then
          placeholderParent.replaceChild(targetNode, placeholder)
        else
          contentContainer.foreach { container =>
            if container.contains(targetNode) then container.removeChild(targetNode)
          }
      case (Some(element), None) =>
        val targetNode = element.ref
        contentContainer.foreach { container =>
          if container.contains(targetNode) then container.removeChild(targetNode)
        }
      case _ =>
        ()

    placeholderNode.foreach { placeholder =>
      Option(placeholder.parentNode).foreach(_.removeChild(placeholder))
    }

    placeholderNode = None
    activeElement = None

    overlayActiveVar.set(false)
    withDocumentBody(_.classList.remove(bodyActiveClass))

  override def getDomElement(): Element = overlayElement

}
