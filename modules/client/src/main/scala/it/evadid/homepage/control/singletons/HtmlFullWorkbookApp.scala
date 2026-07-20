package it.evadid.homepage.control.singletons

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.HtmlWorkbookDomElement

object HtmlFullWorkbookApp {
  
  private lazy val defaults: HomepageDefaults = HomepageDefaults()

  private lazy val initHomepageInfo = HomepageInfo(
    homepageDefaults = defaults,
    currentLanguage = defaults.defaultLanguage,
    workbookInfo = None,
    userInfo = None,
    displayInfo = defaults.defaultDisplay
  )

  lazy val fullInfo: FullInfo = {
    val res = FullInfo(defaults, initHomepageInfo)
    if (res.current.userInfo.isEmpty) {
      res.usageControl.changeUser(Some(defaults.defaultUser))
    }
    res
  }


  private lazy val domElement: Element = HtmlWorkbookDomElement().getDomElement()

  def getDomElement(): Element = domElement
}


