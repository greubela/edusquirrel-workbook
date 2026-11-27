package it.evadid.homepage.control.change

import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.webElements.HtmlAppElement

case class DisplayControl(fullInfo: FullInfo) {

  def closeFullscreen(): Unit = updateDisplay(_.copy(fullscreenElement = None))

  def setFullscreen(element: HtmlAppElement): Unit =
    updateDisplay(_.copy(fullscreenElement = Some(element)))

  def updateDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit =
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(displayInfo = func(curInfo.displayInfo)))

  def changeLanguage(language: HumanLanguage): Unit =
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
}
