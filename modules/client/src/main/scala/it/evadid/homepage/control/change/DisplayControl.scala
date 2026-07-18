package it.evadid.homepage.control.change

import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}

case class DisplayControl(fullInfo: FullInfo) {

  def closeFullscreen(): Unit = {
    fullInfo.homepageInfoState.now().displayInfo.fullscreenElement.foreach {
      case lifecycle: FullscreenLifecycle => lifecycle.onFullscreenClose()
      case _ => ()
    }
    updateDisplay(_.copy(fullscreenElement = None))
  }

  def setFullscreen(element: HtmlAppElement): Unit = {
    updateDisplay(_.copy(fullscreenElement = Some(element)))
  }

  def updateDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = {
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

}
