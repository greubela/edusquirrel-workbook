
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.renderOnDomContentLoaded
import it.evadid.homepage.control.startup.HomepageStartupLogic
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.homepage.workbook.legacy.plantworkshop.PlantWorkshopApp
import org.scalajs.dom

import scala.scalajs.js


@main
def mainApp(): Unit = {
  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) == "undefined") {
    println("MainApp skipped: no document (worker/module import context).")
  } else if (dom.document.getElementById("feedbackDemoRoot") != null) {
    demoStartupLogic()
  } else if(dom.document.getElementById("plantWorkshopApp") != null){
    plantStartupLogic()
  } else {
    HomepageStartupLogic.initHomepage()
  }
}

def demoStartupLogic(): Unit = {
  val domElement = FeedbackDemoElement.element()
  val container = dom.document.getElementById("feedbackDemoRoot")
  if (dom.document.readyState == "loading") renderOnDomContentLoaded(container, domElement)
  else L.render(container, domElement)
}

def plantStartupLogic(): Unit = {
  val domElement = PlantWorkshopApp.appElement
  val container = dom.document.getElementById("plantWorkshopApp")
  if (dom.document.readyState == "loading") renderOnDomContentLoaded(container, domElement)
  else L.render(container, domElement)

}

