package it.evadid.homepage.control.singletons

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.HtmlWorkbookDomElement

object HtmlFullWorkbookApp extends HtmlAppElement {

  private lazy val technical = TechnicalHomepageElements(
    BackendServerConfig.executor,
    //ExecuteOnRemoteServer("http://localhost", 9000),
    //ExecuteOnWebWorker(FileFactory.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),
  )

  private lazy val defaults: HomepageDefaults = HomepageDefaults()

  private lazy val initHomepageInfo = HomepageInfo(
    homepageDefaults = defaults,
    currentLanguage = defaults.defaultLanguage,
    workbookInfo = None,
    userInfo = None,
    displayInfo = defaults.defaultDisplay
  )

  lazy val fullInfo: FullInfo = {
    val res = FullInfo(defaults, technical, initHomepageInfo)
    if (res.current.userInfo.isEmpty) {
      res.control.changeUser(Some(defaults.defaultUser))
    }
    res
  }


  private val domElement: Element = HtmlWorkbookDomElement(fullInfo).getDomElement()

  override def getDomElement(): Element = domElement
}


