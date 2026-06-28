package it.evadid.homepage.control.singletons

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlWorkbookRenderer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger

import scala.concurrent.{ExecutionContext, Future}

object HtmlFullWorkbookApp extends HtmlAppElement {

  private lazy val technical = TechnicalHomepageElements(
    HtmlFullScreenContainerElement(),
    BackendServerConfig.executor,
    //ExecuteOnRemoteServer("http://localhost", 9000),
    //ExecuteOnWebWorker(FileFactory.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),
  )

  private lazy val defaults: HomepageDefaults = HomepageDefaults()

  private lazy val initHomepageInfo = HomepageInfo(
    homepageDefaults = defaults,
    currentLanguage = defaults.defaultLanguage,
    workbookInfo = None,
    userInfo = None
  )

  lazy val fullInfo: FullInfo = {
    val res = FullInfo(defaults, technical, initHomepageInfo)
    if (res.current.userInfo.isEmpty) {
      res.control.changeUser(Some(defaults.defaultUser))
    }
    res
  }

  private lazy val workbookDomElement: Element = {
    val workbookSignal: Signal[Element] = fullInfo.signals.workbook.mapLazy {
      case Some(workbookInfo) => HtmlWorkbookRenderer.render(workbookInfo.loadedWorkbook).getDomElement() //div("HtmlFullWorkbookApp::workbookDomelement not properly re-implemented yet!") //workbook.loadedWorkbook.getDomElement()
      case None => div(text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId("basic/noWorkbookLoaded")))
    }

    div(
      cls := "workbook-app-shell",
      technical.fullScreenContainer.getDomElement(),
      mainTag(
        cls := "workbook-main",
        child <-- workbookSignal
      ),
      initFooter()
    )
  }

  def initFooter(): Element = footerTag(
    cls := "workbook-footer",
    div(
      cls := "workbook-footer-content",
      span(text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId("basic/workbookfooterprivacyinfo")))
    )
  )

  override def getDomElement(): Element = workbookDomElement





}
