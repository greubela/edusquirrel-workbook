
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.renderOnDomContentLoaded
import it.evadid.homepage.control.startup.HomepageStartupLogic
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement

import org.scalajs.dom

import scala.scalajs.js


@main
def mainApp(): Unit = {
  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) == "undefined") {
    println("MainApp skipped: no document (worker/module import context).")
  } else if (dom.document.readyState == "loading") {
    dom.document.addEventListener("DOMContentLoaded", (_: dom.Event) => startBrowserApp())
  } else startBrowserApp()
}

private def startBrowserApp(): Unit = {
  HomepageStartupLogic.preheat()
  if (dom.document.getElementById("feedbackDemoRoot") != null) {
    demoStartupLogic()
  } else {
    HomepageStartupLogic.initHomepage()
  }
}

def demoStartupLogic(): Unit = {
  val root = dom.document.documentElement
  scala.util.Try(dom.window.localStorage.getItem("fd-theme")).toOption
    .filter(value => value == "light" || value == "dark")
    .foreach(value => root.setAttribute("data-theme", value))
  Option(dom.document.getElementById("fdThemeToggle")).foreach(_.addEventListener("click", (_: dom.Event) => {
    val next = if root.getAttribute("data-theme") == "light" then "dark" else "light"
    dom.document.body.classList.add("fd-theme-switching")
    root.setAttribute("data-theme", next)
    scala.util.Try(dom.window.localStorage.setItem("fd-theme", next))
    scala.scalajs.js.timers.setTimeout(380) { dom.document.body.classList.remove("fd-theme-switching") }
  }))
  val domElement = FeedbackDemoElement.element()
  val container = dom.document.getElementById("feedbackDemoRoot")
  if (dom.document.readyState == "loading") renderOnDomContentLoaded(container, domElement)
  else L.render(container, domElement)
}


