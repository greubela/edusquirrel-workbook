package it.evadid.homepage.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.distribution.clients.ExecuteOnWebWorker
import it.evadid.executors.MathExecutor
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.homepage.workbook.legacy.singletons.FileDataStorage
import todomove.datastructures.web.file.FileFactory
import it.evadid.homepage.*
import it.evadid.homepage.control.info.{FullInfo, HomepageDefaults, HomepageInfo}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlWorkbookRenderer

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
      case Some(workbookInfo) => HtmlWorkbookRenderer.render(workbookInfo.loadedWorkbook).getDomElement()//div("HtmlFullWorkbookApp::workbookDomelement not properly re-implemented yet!") //workbook.loadedWorkbook.getDomElement()
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
