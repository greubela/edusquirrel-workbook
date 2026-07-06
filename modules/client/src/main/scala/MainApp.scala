
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.content.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.homepage.workbook.legacy.plantworkshop.PlantWorkshopApp
import org.scalajs.dom

import scala.concurrent.*
import scala.scalajs.js
import scala.util.*

private given ExecutionContextExecutor = ExecutionContext.global

private val tryToLoad: List[String] = List("plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "workbookCompression", "feedbackDemoRoot", "workbookTest")

private def load(containerId: String): Unit = {
  println("loading workbook: " + containerId)
  val domElement = containerId match {
    case "plantWorkshopApp" => {
      PlantWorkshopApp.appElement
    }
    case "workbookEmbroidery" => {
      HtmlFullWorkbookApp.fullInfo.control.changeWorkbook(CreateEmbroideryWorkbook(HtmlFullWorkbookApp.fullInfo))
      HtmlFullWorkbookApp.getDomElement()
    }
    case "workbookTest" => {
      HtmlFullWorkbookApp.fullInfo.control.changeWorkbook(CreateTestWorkbook(HtmlFullWorkbookApp.fullInfo))
      HtmlFullWorkbookApp.getDomElement()
    }
    case "workbookPlantWorkshop" => {
      HtmlFullWorkbookApp.fullInfo.control.changeWorkbook(CreatePlantworkshopWorkbook(HtmlFullWorkbookApp.fullInfo))
      HtmlFullWorkbookApp.getDomElement()
    }
    case "workbookCompression" => {
      HtmlFullWorkbookApp.fullInfo.control.changeWorkbook(CreateCompressionWorkbook(HtmlFullWorkbookApp.fullInfo))
      HtmlFullWorkbookApp.getDomElement()
    }
    case "feedbackDemoRoot" => {
      FeedbackDemoElement.element()
    }
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

  //println("testing some calculations atm :)")

  DownloadHelper.fetchUrl("https://ypcgzj23.trafficplex.cloud/health").onComplete {
    case Success(res) => println("Backend Health check: " + new String(res))
    case Failure(err) => println("Backend Health error: " + err.getMessage)
  }(using ExecutionContext.global)


}


@main
def mainApp(): Unit = {
  //  FullInfo.resetLocalStorage()

  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) != "undefined") {
    val canLoad: List[String] = tryToLoad.flatMap(id => if (dom.document.getElementById(id) != null) Some(id) else None)
    if (canLoad.isEmpty) println("Found no container to load a workbook into. Tried: " + tryToLoad.mkString(", "))
    if (canLoad.size > 1) println("Found more than one workbook to load: " + canLoad.mkString(", "))
    if (canLoad.nonEmpty) {
      val loadBasicsFut: Future[?] = HtmlFullWorkbookApp.fullInfo.signals.contentStorage.ensureDefaultLoaded()
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
    } else {
      println("MainApp skipped: no document (worker/module import context).")
    }
  }
}


