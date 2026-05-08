package workbook.htmlElements

import com.raquo.laminar.api.L.*
import datastructures.web.file.FileDescription
import it.evadid.distribution.clients.{ExecuteOnRemoteServer, ExecuteOnWebWorker}
import it.evadid.executors.MathExecutor
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{FullInfo, HomepageDefaults, HomepageInfo}
import workbook.singletons.FileDataStorage

object HtmlFullWorkbookApp extends HtmlWorkbookElement {


  private lazy val technical = TechnicalHomepageElements(
    HtmlFullScreenContainerElement(),
    FileDataStorage(),
    ExecuteOnRemoteServer("ypcgzj23.trafficplex.cloud", 443),
    ExecuteOnWebWorker(FileDescription.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),
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
      case Some(workbook) => workbook.loadedWorkbook.getDomElement()
      case None => div(text <-- fullInfo.signals.stringFromLanguageMapId("basic/noWorkbookLoaded"))
    }
    val withFullscreen: Signal[List[Element]] = workbookSignal.map(workbookDom => List(technical.fullScreenContainer.getDomElement(), workbookDom))
    div(children <-- withFullscreen)
  }


  override def getDomElement(): Element = workbookDomElement


}
