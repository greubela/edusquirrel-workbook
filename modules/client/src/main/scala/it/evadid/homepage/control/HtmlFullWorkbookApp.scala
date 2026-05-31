package it.evadid.homepage.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.distribution.clients.ExecuteOnWebWorker
import it.evadid.executors.MathExecutor
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.homepage.workbook.legacy.BackendServerConfig
import it.evadid.homepage.workbook.legacy.htmlElements.TechnicalHomepageElements
import it.evadid.homepage.workbook.legacy.htmlElements.container.HtmlFullScreenContainerElement
import it.evadid.homepage.workbook.legacy.model.info.*
import it.evadid.homepage.workbook.legacy.singletons.FileDataStorage
import todomove.datastructures.web.file.FileFactory
import it.evadid.homepage.*

object HtmlFullWorkbookApp extends HtmlAppElement{

  private lazy val technical = TechnicalHomepageElements(
    HtmlFullScreenContainerElement(),
    FileDataStorage(),
    BackendServerConfig.executor,
    ExecuteOnWebWorker(FileFactory.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),
  )

  private val defaults: HomepageDefaults = HomepageDefaults()

  private val initHomepageInfo = HomepageInfo(
    homepageDefaults = defaults,
    currentLanguage = defaults.defaultLanguage,
    workbookInfo = None,
    userInfo = None
  )

  val fullInfo: FullInfo = {
    val res = FullInfo(defaults, technical, initHomepageInfo)
    if (res.current.userInfo.isEmpty) {
      res.control.changeUser(Some(defaults.defaultUser))
    }
    res
  }

  private lazy val workbookDomElement: Element = {
    val workbookSignal: Signal[Element] = fullInfo.signals.workbook.mapLazy {
      case Some(workbook) => div("HtmlFullWorkbookApp::workbookDomelement not properly re-implemented yet!") //workbook.loadedWorkbook.getDomElement()
      case None => div(text <-- fullInfo.signals.stringFromLanguageMapId("basic/noWorkbookLoaded"))
    }
    val withFullscreen: Signal[List[Element]] = workbookSignal.map(workbookDom => List(technical.fullScreenContainer.getDomElement(), workbookDom))
    div(children <-- withFullscreen)
  }

  override def getDomElement(): Element = workbookDomElement


}
