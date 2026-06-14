
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.content.{CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.homepage.workbook.legacy.plantworkshop.PlantWorkshopApp
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.scalajs.js
import scala.util.*

private val tryToLoad: List[String] = List("plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "feedbackDemoRoot")

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
    case "workbookPlantWorkshop" => {
      HtmlFullWorkbookApp.fullInfo.control.changeWorkbook(CreatePlantworkshopWorkbook(HtmlFullWorkbookApp.fullInfo))
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

private def testCalculations(): Unit = {

  //println("testing some calculations atm :)")

  DownloadHelper.fetchUrl("https://ypcgzj23.trafficplex.cloud/health").onComplete(res => {
    println("Backend Health check: " + res.toString)
  })(using ExecutionContext.global)

  /*
    val systemPrompt: String = "Please entertain this human :-)"
    val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.technical.backendServerExecutor
    val request: MessengerChatCompletionRequest = MessengerChatCompletionRequest(systemPrompt, MessengerModel.testCompletion)
    val resultFut: Future[ExecutionInfo] = LLMCommands.completeLLMCommandFactory.sendCommandTo(backend, Logger(), request)

    resultFut.onComplete(res => println("future completed: " + res))(using ExecutionContext.global)
  */
}

@main
def mainApp(): Unit = {
  //  FullInfo.resetLocalStorage()

  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) != "undefined") {
    val canLoad: List[String] = tryToLoad.flatMap(id => if (dom.document.getElementById(id) != null) Some(id) else None)
    if (canLoad.isEmpty) println("Found no container to load a workbook into. Tried: " + tryToLoad.mkString(", "))
    if (canLoad.size > 1) println("Found more than one workbook to load: " + canLoad.mkString(", "))
    if (canLoad.nonEmpty) {
      val loadBasicsFut = HtmlFullWorkbookApp.fullInfo.signals.contentStorage.futureForDefaultsLoaded
      loadBasicsFut.onComplete(finished => load(canLoad.head))(using ExecutionContext.global)
    }
    testCalculations()
  } else {
    println("MainApp skipped: no document (worker/module import context).")
  }

}


object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main
