
import it.evadid.homepage.control.startup.HomepageStartupLogic

import scala.scalajs.js


@main
def mainApp(): Unit = {
  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) != "undefined") {
    HomepageStartupLogic.initHomepage()
  } else {
    println("MainApp skipped: no document (worker/module import context).")
  }
}


