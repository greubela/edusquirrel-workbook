package it.evadid.homepage.control.change

import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo}
import it.evadid.homepage.webElements.HtmlAppElement

case class DisplayControl(fullInfo: FullInfo) {

  private var onFullscreenClosed: () => Unit = () => ()

  private def notifyFullscreenClosed(): Unit = {
    val callback = onFullscreenClosed
    // Clear first so that repeated closes and callback-triggered replacements
    // cannot invoke the same callback more than once.
    onFullscreenClosed = () => ()
    callback()
  }

  def closeFullscreen(): Unit = {
    notifyFullscreenClosed()
    updateDisplay(_.copy(fullscreenElement = None))
  }

  /** Show an element and invoke `onClose` once when it is closed or replaced. */
  def setFullscreen(element: HtmlAppElement, onClose: () => Unit = () => ()): Unit = {
    notifyFullscreenClosed()
    onFullscreenClosed = onClose
    updateDisplay(_.copy(fullscreenElement = Some(element)))
  }

  def updateDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = {
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

}
