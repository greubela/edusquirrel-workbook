package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.keys.EventProp
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.structureRenderer.HtmlWorkbookRenderer
import org.scalajs.dom

case class HtmlWorkbookDomElement(fullInfo: FullInfo) extends HtmlAppElement {

  private lazy val workbookDomSignal: Signal[Element] = fullInfo.signals.workbook.mapLazy {
    case Some(workbookInfo) => HtmlWorkbookRenderer.renderAppElement(workbookInfo.loadedWorkbook).getDomElement()
    case None => div(text <-- laminarHelper.plaintextStringSignal("basic/noWorkbookLoaded"))
  }

  private lazy val fullscreenActiveElementSignal: Signal[Option[HtmlAppElement]] = fullInfo.signals.currentDisplayInfo.map(_.fullscreenElement)

  private val onCloseDialog = new EventProp[dom.Event]("close")

  private lazy val dialogElement: Element = {
    dialogTag(
      cls := "fullscreen-overlay-dialog",
      onCloseDialog --> (_ => fullInfo.displayControl.closeFullscreen()),
      laminarHelper.onClickedOutside(_ => fullInfo.displayControl.closeFullscreen()),
      onMountCallback { ctx =>
        val nativeDialog = ctx.thisNode.ref.asInstanceOf[dom.html.Dialog]

        fullscreenActiveElementSignal.map(_.nonEmpty).foreach { isOpen =>
          if (isOpen && !nativeDialog.open) {
            nativeDialog.showModal()
          } else if (!isOpen && nativeDialog.open) {
            nativeDialog.close()
          }
        }(ctx.owner)
      },
      div(
        cls("fullscreen-content-container"),
        child <-- fullscreenActiveElementSignal.map(_.map(_.getDomElement()).getOrElse(span("nothing to see here :)")))
      )
    )
  }

  private lazy val workbookDomElement: Element = {
    div(
      cls := "workbook-app-shell",
      dialogElement,
      mainElement
    )
  }

  private lazy val mainElement: Element = mainTag(
    cls := "workbook-main",
    child <-- workbookDomSignal
  )

  override def getDomElement(): Element = workbookDomElement
}


/*

case class HtmlWorkbookDomElement(fullInfo: FullInfo) extends HtmlAppElement {

  private lazy val workbookDomSignal: Signal[Element] = fullInfo.signals.workbook.mapLazy {
    case Some(workbookInfo) => HtmlWorkbookRenderer.renderAppElement(workbookInfo.loadedWorkbook).getDomElement()
    case None => div(text <-- laminarHelper.plaintextStringSignal("basic/noWorkbookLoaded"))
  }

  private lazy val fullscreenActiveElementSignal: Signal[Option[HtmlAppElement]] = fullInfo.signals.currentDisplayInfo.map(_.fullscreenElement)
  private lazy val fullscreenActiveCssStringSignal: Signal[String] = fullscreenActiveElementSignal.map(_.map(_ => "fullscreen-active").getOrElse("fullscreen-inactive"))

  private lazy val fullscreenElement: Element = div(
    cls <-- fullscreenActiveCssStringSignal.map(_ + " fullscreen-overlay"),
    L.onKeyDown --> (event => if (event.key == "Escape") then {
      event.preventDefault()
      fullInfo.displayControl.closeFullscreen()
    }),
    div(
      cls := "fullscreen-content-container",
      child <-- fullscreenActiveElementSignal.map(_.map(_.getDomElement()).getOrElse(span("")))
    ),
    // todo : make proper aesthetics...
    div(
      typ := "button",
      cls := "fullscreen-close-button",
      aria.label := "Exit full screen",
      title := "Exit full screen",
      "×",
      onClick --> (_ => fullInfo.displayControl.closeFullscreen())
    )
  )

  private lazy val mainElement: Element = mainTag(
    cls := "workbook-main",
    child <-- workbookDomSignal
  )

  private val onCloseDialog = customHtmlEvent[dom.Event]("close")

  private lazy val dialogElement: Element = {
    // Use a native HTML5 dialog element
    dialogTag(
      cls := "fullscreen-overlay-dialog",
      htmlEvents.createCustomEvent("close") --> (_ => fullInfo.displayControl.closeFullscreen()),

      onMountCallback { ctx =>
        val nativeDialog = ctx.thisNode.ref.asInstanceOf[dom.html.Dialog]

        fullscreenActiveElementSignal.map(_.nonEmpty).foreach { isOpen =>
          if (isOpen && !nativeDialog.open) {
            nativeDialog.showModal() // Öffnet das native Modal im Top-Layer
          } else if (!isOpen && nativeDialog.open) {
            nativeDialog.close() // Schließt es sauber
          }
        }(ctx.owner) // Bindet den Lifecycle an das Element, verhindert Memory Leaks!
      },
      div(
        cls("fullscreen-content-container"),
        child <-- fullscreenActiveElementSignal.map(_.map(_.getDomElement()).getOrElse(span("nothing to see here :)")))
      ),
      div(
        typ := "button",
        cls := "fullscreen-close-button",
        aria.label := "Exit full screen",
        title := "Exit full screen",
        "×",
        onClick --> (_ => fullInfo.displayControl.closeFullscreen())
      )
    )
  }

  private lazy val workbookDomElement: Element = {
    div(
      cls := "workbook-app-shell",
      dialogElement,
      mainElement
    )
  }

  override def getDomElement(): Element = workbookDomElement


}
*/