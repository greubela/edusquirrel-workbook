
import com.raquo.laminar.api.L.*
import content.{CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, plantworkshop}
import interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import it.evadid.distribution.clients.*
import it.evadid.executors.MathExecutor
import org.scalajs.dom
import workbook.htmlElements.HtmlFullWorkbookApp

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
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
 /* val executors = List(MathExecutor())
  val clients: List[ExecutionClient] = List(ImmediateExecution(executors), AsyncExecution(executors), ServerExecution("127.0.0.1", 9000))
*/
  /*clients.foreach(curClient => {
    curClient.executeCommand(ExecutionCommand("add", Map("a" -> "1", "b" -> "2"))).onComplete {
      case Success(result) => println(s"${curClient.getClass} success: " + result)
      case Failure(err) => println(s"${curClient.getClass} error: " + err.getMessage())
    }
  })*/
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
