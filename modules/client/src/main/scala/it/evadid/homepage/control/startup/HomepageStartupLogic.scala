package it.evadid.homepage.control.startup

import com.raquo.laminar.api.L.*
import it.evadid.distribution.command.SerializedException
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.content.{CreateCompressionWorkbook, CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, CreateTestWorkbook}
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.scalajs.js
import scala.util.{Failure, Success}

object HomepageStartupLogic {

  private given ExecutionContext = ExecutionContext.global

  private given ExecutionContextExecutor = ExecutionContext.global

  private val tryToLoad: List[String] = List("landingPage", "loginPage", "plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "workbookCompression", "workbookTest")
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

    val futureLoadBasics = HtmlFullWorkbookApp.fullInfo.contentControl.languageStorage.ensureDefaultLanguageSourcesLoaded().recover { err =>
      logger.logExceptionWarn("ignoring basics which should have been loaded", err)
    }

    for {
      basicsLoaded <- futureLoadBasics
    } {

      val workbook: Option[AllWorkbookInfo] = canLoad.headOption.flatMap(loadWorkbookById)
      workbook.foreach(workbook => print("Workbook: \n" + workbook.loadedWorkbook.toJson + "\n\n"))
      fullInfo.usageControl.changeWorkbook(workbook)

      val futureTestCalc = testCalculations().recover { err =>
        logger.logExceptionWarn("testCalculations failed", err)
      }

      val futureAutoLogin = fullInfo.usageControl.tryAutoLogin().recover { err =>
        logger.logWarn(s"auto login was not possible: ${err.getMessage}")
      }

      renderElementIntoApp(logger, HtmlFullWorkbookApp.getDomElement())
    }
  }

  def loadWorkbookById(workbookId: String): Option[AllWorkbookInfo] = workbookId match {
    case "workbookEmbroidery" => Some(CreateEmbroideryWorkbook(HtmlFullWorkbookApp.fullInfo).createEverything)
    case "workbookTest" => Some(CreateTestWorkbook(HtmlFullWorkbookApp.fullInfo).createEverything)
    case "workbookPlantWorkshop" => Some(CreatePlantworkshopWorkbook(HtmlFullWorkbookApp.fullInfo).createEverything)
    case "workbookCompression" => Some(CreateCompressionWorkbook(HtmlFullWorkbookApp.fullInfo).createEverything)
    case _ => None
  }

  private def initWorkbookOnlyAfterDependenciesLoaded: Boolean = {
    val configValue = js.Dynamic.global.selectDynamic("EDUSQUIRREL_INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED")
    if (js.isUndefined(configValue)) true else configValue.asInstanceOf[Boolean]
  }

  private def testCalculations(): Future[?] = Future {
    HtmlFullWorkbookApp.fullInfo.contentControl.fileFactory.onBackendServer("/health").loadData().onComplete {
      case Success(res) => println("Backend Health check: " + new String(res.fileDataAsUtf8String))
      case Failure(err) => println("Backend Health error: " + err.getMessage)
    }(using ExecutionContext.global)

    println("uuid: " + java.util.UUID.randomUUID())

    /*
    val testMail = SendMailRequest("andre.greubel@hu-berlin.de", "This is a test mail :)", "This is the mail body!")
    val mailRes = MailCommands.sendMailCommand.sendCommandTo(fullInfo.defaults.defaultBackend.executor, testMail, Some(fullInfo.loggerSystemInfo.debugFuncLogger))
    mailRes.onComplete(res => println("[UGLY PRINTLN HOMEPAGESTARTUPLOGIC] res of mail cmd: " + mailRes))
  */

  }
}


