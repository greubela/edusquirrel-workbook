package it.evadid.homepage.control.startup

import com.raquo.laminar.api.L.*
import it.evadid.distribution.command.SerializedException
import it.evadid.facades.{CodeMirror, JSXGraph, Snap, Workers}
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY.CONTINUE_IMMEDIATELY
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.content.DigitalWorkbookCatalog
import it.evadid.homepage.workbook.content.test.CreateTestWorkbook
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


object HomepageStartupLogic {
  private val preheatingStarted = Var(false)
  def preheat(): Unit = if !preheatingStarted.now() then {
    preheatingStarted.set(true)
    // Start requests immediately; never await them from the render path.
    CodeMirror.preload().failed.foreach(_ => ())
    Workers.preheat()
    val turtlePages = List("workbookTest", "workbookEmbroidery", "workbookEmbroideryPreview", "workbookMonks")
    if turtlePages.exists(id => dom.document.getElementById(id) != null) then {
      Snap.preload().failed.foreach(_ => ())
      JSXGraph.preload().failed.foreach(_ => ())
    }
  }


  private val tryToLoad: List[String] = List("landingPage", "loginPage", "plantWorkshopApp", "workbookEmbroidery", "workbookEmbroideryPreview", "workbookPlantWorkshop", "workbookCompression", "workbookMonks", "workbookTest", "workbookPhishing", "workbookImageRecognition", "workbookBlockchain", "workbookEvacuation")
  private val canLoad: List[String] = tryToLoad.flatMap(id => if (dom.document.getElementById(id) != null) Some(id) else None)

  def renderElementIntoApp(logger: Logger, domElement: Element): Unit = {
    if (canLoad.isEmpty) {
      logger.logException(SerializedException("Cannot load content, because there is no known container id to render it into :-("))
      dom.document.body.appendChild(div(s"Content cannot be loaded, look at the console to find out more :-(").ref)
    } else {
      val container = dom.document.getElementById(canLoad.head)
      container.children.foreach(container.removeChild(_))
      if (dom.document.readyState == "loading") renderOnDomContentLoaded(container, domElement)
      else render(container, domElement)
    }
  }

  def initHomepage(): Unit = {
    val fullInfo = HtmlFullWorkbookApp.fullInfo
    val logger = fullInfo.loggerSystemInfo.contentControlLogger

    preheat()
    val workbook: Option[AllWorkbookInfo] = canLoad.headOption.flatMap(loadWorkbookById)
    fullInfo.usageControl.changeWorkbook(workbook)
    workbook.foreach { info =>
      val languages = info.loadedWorkbook.metadata.availableLanguages
      if (!languages.contains(fullInfo.homepageInfoNow().currentLanguage))
        languages.headOption.foreach(fullInfo.usageControl.changeLanguage)
    }

    // Mount first. Cache restoration and remote language refresh update its signals later.
    renderElementIntoApp(logger, HtmlFullWorkbookApp.getDomElement())
    fullInfo.contentControl.languageStorage.ensureStartup(CONTINUE_IMMEDIATELY).failed.foreach { err =>
      logger.logExceptionWarn("Language startup unavailable", err)
    }
    fullInfo.usageControl.tryAutoLogin().failed.foreach { err =>
      logger.logWarn(s"auto login was not possible: ${err.getMessage}")
    }
  }

  def loadWorkbookById(workbookId: String): Option[AllWorkbookInfo] =
    if (workbookId == "workbookTest") Some(CreateTestWorkbook(HtmlFullWorkbookApp.fullInfo).createEverything)
    else DigitalWorkbookCatalog.nativeWorkbook(workbookId)
      .map(_.factory(HtmlFullWorkbookApp.fullInfo).createEverything)

}
