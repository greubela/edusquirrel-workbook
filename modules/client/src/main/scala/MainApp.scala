
import com.raquo.laminar.api.L.*
import content.{CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, plantworkshop}
import interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.*
import it.evadid.util.Logger
import org.scalajs.dom
import workbook.htmlElements.HtmlFullWorkbookApp

import scala.util.*
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.scalajs.js

private val tryToLoad: List[String] = List("plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "feedbackDemoRoot")

private def load(containerId: String): Unit = {
  println("loading workbook: " + containerId)
  val domElement = containerId match {
    case "plantWorkshopApp" => {
      plantworkshop.PlantWorkshopApp.appElement
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

  println("testing some calculations atm :)")

  val systemPrompt: String = "You are a helpful and smart assistant teacher. The teacher is speaking with the student. If you are requested to continue the conversation and help the student (because the teacher is not available right now), please do so in the language of the student. Note not to give away answers!"

  val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.technical.backendServerExecutor
  val request: MessengerChatCompletionRequest = MessengerChatCompletionRequest(systemPrompt, MessengerModel.testCompletion())
  val resultFut: Future[ExecutionInfo] = LLMCommands.completeLLMCommandFactory.sendCommandTo(backend, Logger(), request)

  resultFut.onComplete(res => println("future completed: " + res))(using ExecutionContext.global)

}

@main
def mainApp(): Unit = {
  //  FullInfo.resetLocalStorage()

  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) != "undefined") {
    val canLoad: List[String] = tryToLoad.flatMap(id => if (dom.document.getElementById(id) != null) Some(id) else None)
    if (canLoad.isEmpty) println("Found no container to load a workbook into. Tried: " + tryToLoad.mkString(", "))
    if (canLoad.size > 1) println("Found more than one workbook to load: " + canLoad.mkString(", "))
    if (canLoad.nonEmpty) load(canLoad.head)
    testCalculations()
  } else {
    println("MainApp skipped: no document (worker/module import context).")
  }

}


object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main
