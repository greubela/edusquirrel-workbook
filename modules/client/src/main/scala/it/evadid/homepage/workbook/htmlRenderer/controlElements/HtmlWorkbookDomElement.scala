package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.keys.EventProp
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT_UNDERSCORE_REPLACABLE
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement

case class HtmlWorkbookDomElement() extends HtmlAppElement {

  override def getDomElement(): Element = workbookDomElement

  private lazy val domElement: ReactiveHtmlElement[HTMLDivElement] = workbookDomElement

  private lazy val workbookDomElement: ReactiveHtmlElement[HTMLDivElement] = {
    div(
      cls := "workbook-app-shell",
      dialogElement,
      mainTag(
        cls := "workbook-main", div(
          cls := "it/evadid/homepage/workbook",
          children <-- workbookBodyDomSignal.map(createDomElement)
        )
      )
    )
  }
  // BODY

  private lazy val workbookBodyDomSignal: Signal[Element] = {
    val workbookSignal = fullInfo.signals.workbook
    val userSignal = fullInfo.signals.user
    val watchSignal = workbookSignal.combineWith(userSignal)

    watchSignal.map {
      case (_, None) => HtmlLoginElement().getDomElement()
      case (None, _) => HtmlSelectWorkbookElement().getDomElement()
      case (Some(workbookInfo), Some(userInfo)) => HtmlWorkbookBodyElement(workbookInfo, userInfo).getDomElement()
    }
  }

  // HEADER AND FOOTER

  private def collapsedSignal: Signal[Boolean] = fullInfo.signals.display.map(_.collapsedNavigation)


  lazy val privacyFooterStringSignal: Signal[String] = {
    fullInfo.signals.user.flatMapSwitch(allUserInfoOp => {
      if (allUserInfoOp.isEmpty) laminarHelper.plaintextStringSignal("login/privacyFooterNoLogin")
      else if (!allUserInfoOp.get.config.isOnlineAccount) laminarHelper.plaintextStringSignal("login/privacyFooterLocalLogin")
      else {
        val syncDest = allUserInfoOp.get.config.syncDestinations
        val syncDestStr = syncDest.map(HomepageDefaults.defaultSyncLocationSerializer.serialize)
        laminarHelper.contentIdStringSignal(LanguageMapContentId("login/privacyFooterOnlineLogin"), PLAINTEXT_UNDERSCORE_REPLACABLE, List(syncDest.size.toString, syncDestStr.mkString("(", ", ", ")")))
      }
    })

  }

  private def createDomElement(bodyDom: Element): List[Element] = List(
    div(
      cls := "workbook-header",
      children <-- headerChildren
    ),
    bodyDom,
    footerTag(
      cls := "workbook-footer",
      div(
        cls := "workbook-footer-content",
        span(text <-- privacyFooterStringSignal)
      )
    )
  )


  lazy val headerChildren: Signal[List[Element]] = {
    fullInfo.signals.workbook.combineWith(fullInfo.signals.user, collapsedSignal).map(
      (workbook, user, collapsed) => {
        val always = List(
          createTitleLine(workbook, user),
          DropdownMenuControl(workbook, user).getDomElement(),
        )
        val section: List[Element] = if (collapsed || workbook.isEmpty || user.isEmpty) List() else List(SectionSelectionLine(workbook.get).getDomElement())
        val language: List[Element] = if (collapsed) List() else List(LanguageSelectionLine().getDomElement())
        always ++ language ++ section ++ List(createDomToggleButton())
      })

  }


  private def createDomToggleButton(): Element = div(
    cls := "workbook-header-toggle",
    onClick --> { _ => fullInfo.usageControl.changeDisplay(displayInfo => displayInfo.copy(collapsedNavigation = !displayInfo.collapsedNavigation)) },
    span(
      child <-- collapsedSignal.map { c =>
        if (c) span(text <-- laminarHelper.plaintextStringSignal("basic/showHeader"))
        else span(text <-- laminarHelper.plaintextStringSignal("basic/hideHeader"))
      }
    )
  )

  private def createTitleLine(workbook: Option[AllWorkbookInfo], user: Option[AllUserInfo]): Element = {
    if (user.isEmpty) {
      div(
        cls := "workbook-title-line",
        h1(text <-- laminarHelper.plaintextStringSignal("login/titleLoginPage")),
      )
    }
    else if (workbook.isEmpty)
      div(
        cls := "workbook-title-line",
        h1(text <-- laminarHelper.plaintextStringSignal("basic/titleWorkbookSelectionPage")),
      )
    else {
      div(
        cls := "workbook-title-line",
        h1(text <-- laminarHelper.plaintextStringSignal(workbook.get.loadedWorkbook.workbookTitle)),
      )
    }
  }

  // FULLSCREEN STUFF


  private lazy val fullscreenActiveElementSignal: Signal[Option[HtmlAppElement]] = fullInfo.signals.currentDisplayInfo.map(_.fullscreenElement)

  // Closing the native dialog must not remove its content. In particular,
  // Morphic binds input directly to its canvas and cannot safely be recreated
  // on every open. Retain the last child while the dialog itself is closed;
  // selecting a different fullscreen element still replaces and unmounts it.
  private lazy val retainedFullscreenElementSignal: Signal[Option[HtmlAppElement]] =
    fullscreenActiveElementSignal
      .scanLeft(identity[Option[HtmlAppElement]])((retained, active) => active.orElse(retained))
      .distinct

  private val onCloseDialog = new EventProp[dom.Event]("close")
  private val onCancelDialog = new EventProp[dom.Event]("cancel")

  private def currentAllowsOutsideDismiss: Boolean =
    fullInfo.signals.currentDisplayInfo.now().fullscreenElement match
      case Some(lifecycle: FullscreenLifecycle) => lifecycle.dismissOnOutsideClick
      case _ => true

  private lazy val dialogElement: Element = {
    dialogTag(
      cls := "fullscreen-overlay-dialog",
      position.relative,
      onCloseDialog --> (_ => fullInfo.displayControl.closeFullscreen()),
      // Light-dismiss (backdrop) of <dialog> — skip for editors that opt out.
      onCancelDialog --> { ev =>
        if !currentAllowsOutsideDismiss then ev.preventDefault()
      },
      laminarHelper.onClickedOutside { _ =>
        if currentAllowsOutsideDismiss then fullInfo.displayControl.closeFullscreen()
      },
      onMountCallback { ctx =>
        val nativeDialog = ctx.thisNode.ref.asInstanceOf[dom.html.Dialog]

        fullscreenActiveElementSignal.map(_.nonEmpty).foreach { isOpen =>
          if (isOpen && !nativeDialog.open) {
            nativeDialog.showModal()
          } else if (!isOpen && nativeDialog.open) {
            nativeDialog.close()
          }
        }(using ctx.owner)
      },
      div(
        cls("fullscreen-content-container"),
        child <-- retainedFullscreenElementSignal.map(_.map(_.getDomElement()).getOrElse(span("nothing to see here :)")))
      ),
      button(
        typ := "button",
        cls := "fullscreen-close-button",
        aria.label := "Exit full screen",
        title := "Exit full screen",
        "×",
        onClick --> (_ => fullInfo.displayControl.closeFullscreen())
      )
    )
  }

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