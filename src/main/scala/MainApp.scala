
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.{CreateEmbroideryWorkbook, CreatePlantworkshopWorkbook, TestWorkbookFactory, plantworkshop}
import content.plantworkshop.PlantWorkshopApp
import org.scalajs.dom
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.info.AllWorkbookInfo
import `export`.workers.MathWorkerClient
import `export`.workers.client.TurtleStitchWorkerClient
import com.raquo.laminar.api.L.unsafeWindowOwner

import `export`.traits.WorkerTraits.WorkerState

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*


private def info = AllWorkbookInfo.singleton
private def workbookInfoVar = AllWorkbookInfo.singleton.workbookInfoVar
private def fullscreenElement = AllWorkbookInfo.singleton.technicalElements.fullScreenContainer

private def idAndContentList: List[(String, Element)] = List(
  ("plantWorkshopApp", plantworkshop.PlantWorkshopApp.appElement),
  ("workbookPlantWorkshop", CreatePlantworkshopWorkbook(info).createWorkbook.getDomElement()),
  //("testEditor", HtmlFullscreenTurtleEditorElement(BeProgram.debugGraphicsProgram().fullProgram).getDomElement()),
  //("workbookTest", TestWorkbookFactory.createTestWorkbook(AllWorkbookInfo.singleton.technicalElements.fullScreenContainer).getDomElement()),
  //("worksheetMonks", TestWorkbook(fullscreenElement).getDomElement()),
  ("workbookEmbroidery", CreateEmbroideryWorkbook(info).createWorkbook.getDomElement()),
)

def insertWorkbookContent(): Unit = {

  def tryToLoad(containerId: String, contentElement: Element): Unit = {
    println("try to load: " + containerId)
    val container = dom.document.getElementById(containerId)
    if (container != null) {
      println("Loading Content: " + containerId)
      val combinedElement = div(
        fullscreenElement.getDomElement(),
        contentElement
      )
      if (dom.document.readyState == "loading") {
        renderOnDomContentLoaded(container, combinedElement)
      } else {
        render(container, combinedElement)
      }
    }
  }
  idAndContentList.foreach { case (id, contentElement) => tryToLoad(id, contentElement) }
}

def initWorkbook(): Unit = {
  insertWorkbookContent()
/*
  val mathWorkerClient = new MathWorkerClient()
  mathWorkerClient.serverStateSignal.foreach(newSig => println("MathWorker status changed: " + newSig.toString))(unsafeWindowOwner)
  mathWorkerClient.add(7, 5).foreach(result => println(s"MathWorker add(7, 5) = $result"))
  mathWorkerClient.multiply(7, 5).foreach(result => println(s"MathWorker multiply(7, 5) = $result"))

  val turtleWorker = new TurtleStitchWorkerClient(dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas])
  turtleWorker.serverStateSignal.foreach(newSig => println("TurtleWorker status changed: " + newSig.toString))(unsafeWindowOwner)

  val turtleDemoProjectUrl = "../resources/workbookresources/embroidery/existingProjects/simple_forward.xml"
  dom.fetch(turtleDemoProjectUrl).toFuture
    .flatMap(_.text().toFuture)
    .foreach { xml =>
      turtleWorker.snapshotGreenFlagProgramsPngDataUrl(xml, "en").onComplete {
        case scala.util.Success(png) =>
          println("TurtleWorker snapshotGreenFlagProgramsPngDataUrl success, prefix: " + png.take(64))
        case scala.util.Failure(err) =>
          println("TurtleWorker snapshotGreenFlagProgramsPngDataUrl failure: " + err.getMessage)
      }
      turtleWorker.getGreenFlagAsLispCode(xml, "en").onComplete {
        case scala.util.Success(lisp) =>
          println("TurtleWorker getGreenFlagAsLispCode success: " + lisp)
        case scala.util.Failure(err) =>
          println("TurtleWorker getGreenFlagAsLispCode failure: " + err.getMessage)
      }
    }

 */

}


@main
def mainApp(): Unit = {
  if (js.typeOf(js.Dynamic.global.selectDynamic("document")) != "undefined") {
    //resetLocalStorage()
    initWorkbook()
  }else{
    println("MainApp skipped: no document (worker/module import context).")
  }

}

def resetLocalStorage(): Unit = {
  val map = (0 until dom.window.localStorage.length)
    .flatMap { i =>
      Option(dom.window.localStorage.key(i)).flatMap { key =>
        Option(dom.window.localStorage.getItem(key)).map(value => key -> value)
      }
    }
    .toMap

  println("[WARN] resetting local storage in MainApp!")

  map.keys.foreach(curKey => {
    println(curKey.toString + " -> " + map(curKey))
  })


  dom.window.localStorage.clear()
}


def insertWorkbook(): Unit = {

}

object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main
