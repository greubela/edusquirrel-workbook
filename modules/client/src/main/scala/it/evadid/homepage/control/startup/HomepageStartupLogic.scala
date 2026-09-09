package it.evadid.homepage.control.startup

import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.content.{CreateCompressionWorkbook, CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, CreateTestWorkbook}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.homepage.workbook.legacy.plantworkshop.PlantWorkshopApp
import it.evadid.workbook.elements.structureElements.Workbook
import org.scalajs.dom
import com.raquo.laminar.api.L.*

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.scalajs.js
import scala.util.{Failure, Success}

object HomepageStartupLogic {

  def initHomepage(): Unit = {
    mainApp()
  }

  /*
  def onStartup(userFromLocalStorage: Option[User], workbook: Option[Workbook], domElement: Option[Element]): Unit = {

  }

  private lazy val containerWorkbookMap: Map[String, Workbook] = Map(
  )
  */

  def mainApp(): Unit = {
    val canLoad: List[String] = tryToLoad.flatMap(id => if (dom.document.getElementById(id) != null) Some(id) else None)
    if (canLoad.isEmpty) println("Found no container to load a workbook into. Tried: " + tryToLoad.mkString(", "))
    if (canLoad.size > 1) println("Found more than one workbook to load: " + canLoad.mkString(", "))
    if (canLoad.nonEmpty) {
      val loadBasicsFut: Future[?] = HtmlFullWorkbookApp.fullInfo.contentControl.languageStorage.ensureDefaultLanguageSourcesLoaded()
      loadBasicsFut.onComplete {
        case Success(_) => println("finished loading!")
        case Failure(err) => err.printStackTrace()
      }(using ExecutionContext.global)

      if (initWorkbookOnlyAfterDependenciesLoaded) {
        loadBasicsFut.onComplete {
          case Success(_) =>
            load(canLoad.head)
            testCalculations()
          case Failure(_) => println("MainApp skipped workbook initialization because dependencies failed to load.")
        }(using ExecutionContext.global)
      } else {
        load(canLoad.head)
        testCalculations()
      }
    }
  }

  private given ExecutionContextExecutor = ExecutionContext.global

  private val tryToLoad: List[String] = List("plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "workbookCompression", "feedbackDemoRoot", "workbookTest")

  private def load(containerId: String): Unit = {
    println("loading workbook: " + containerId)
    val domElement = containerId match {
      case "plantWorkshopApp" =>
        PlantWorkshopApp.appElement
      case "workbookEmbroidery" =>
        HtmlFullWorkbookApp.fullInfo.usageControl.changeWorkbook(CreateEmbroideryWorkbook(HtmlFullWorkbookApp.fullInfo))
        HtmlFullWorkbookApp.getDomElement()
      case "workbookTest" =>
        HtmlFullWorkbookApp.fullInfo.usageControl.changeWorkbook(CreateTestWorkbook(HtmlFullWorkbookApp.fullInfo))
        HtmlFullWorkbookApp.getDomElement()
      case "workbookPlantWorkshop" =>
        HtmlFullWorkbookApp.fullInfo.usageControl.changeWorkbook(CreatePlantworkshopWorkbook(HtmlFullWorkbookApp.fullInfo))
        HtmlFullWorkbookApp.getDomElement()
      case "workbookCompression" =>
        HtmlFullWorkbookApp.fullInfo.usageControl.changeWorkbook(CreateCompressionWorkbook(HtmlFullWorkbookApp.fullInfo))
        HtmlFullWorkbookApp.getDomElement()
      case "feedbackDemoRoot" =>
        FeedbackDemoElement.element()

      case other => div("Workbook '" + other + "' not available via MainApp::load!")
    }

    val container = dom.document.getElementById(containerId)

    if (dom.document.readyState == "loading") renderOnDomContentLoaded(container, domElement)
    else render(container, domElement)
  }

  private def initWorkbookOnlyAfterDependenciesLoaded: Boolean = {
    val configValue = js.Dynamic.global.selectDynamic("EDUSQUIRREL_INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED")
    if (js.isUndefined(configValue)) true else configValue.asInstanceOf[Boolean]
  }

  private def testCalculations(): Unit = {
    HtmlFullWorkbookApp.fullInfo.contentControl.fileFactory.onBackendServer("/health").loadData().onComplete {
      case Success(res) => println("Backend Health check: " + new String(res.fileDataAsUtf8String))
      case Failure(err) => println("Backend Health error: " + err.getMessage)
    }(using ExecutionContext.global)

  }

}
