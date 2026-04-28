
import com.raquo.laminar.api.L.*
import content.{CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, plantworkshop}
import interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import org.scalajs.dom
import workbook.model.info.FullInfo

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.scalajs.js
private def info = FullInfo.singleton

private val tryToLoad: List[String] = List("plantWorkshopApp", "workbookEmbroidery", "workbookPlantWorkshop", "feedbackDemoRoot")

private def load(containerId: String): Unit = {
  println("loading workbook: " + containerId)
  val domElement = containerId match {
    case "plantWorkshopApp" => {
      plantworkshop.PlantWorkshopApp.appElement
    }
    case "workbookEmbroidery" => {
      info.control.changeWorkbook(CreateEmbroideryWorkbook(info))
      info.getDomElement()
    }
    case "workbookPlantWorkshop" => {
      info.control.changeWorkbook(CreatePlantworkshopWorkbook(info))
      info.getDomElement()
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
  println("still alive 444 ?")
}

@main
def mainApp(): Unit = {
  //  FullInfo.resetLocalStorage()

  FullInfo.setDummyUser()

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
