package it.evadid.homepage.control.singletons

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.HtmlWorkbookDomElement

object HtmlFullWorkbookApp {

 // private lazy val homepageDefaults: HomepageInfo = HomepageInfo()

  private lazy val defaultHomepageInfo = HomepageInfo(
    currentLanguage = AppLanguage.English,
    workbookInfo = None,
    userInfo = None,
    displayInfo = AllDisplayInfo(
      collapsedNavigation = false,
      fullscreenElement = None
    ),
    languageMapStore = LanguageMapStorage.empty
  )

  lazy final val fullInfo: FullInfo = FullInfo(defaultHomepageInfo)

  private lazy val domElement: Element = HtmlWorkbookDomElement().getDomElement()

  def getDomElement(): Element = domElement
}


