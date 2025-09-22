
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.image.ImageDescription
import contentmanagement.storage.FileIO
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.workbookHtmlElements.DummyWorkbookExercise

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.language.postfixOps

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

@main
def mainApp(): Unit = {

  doOnStartup()

  insertWorkbook()
}


def insertWorkbook(): Unit = {

  val testEx = DummyWorkbookExercise()
  val worksheetDiv = document.getElementById("worksheetDts")

  if (dom.document.readyState == "loading") {
    renderOnDomContentLoaded(worksheetDiv, testEx.createDomElement())
  }else{
    render(worksheetDiv, testEx.createDomElement())
  }
}

def doOnStartup(): Unit = {
  val testImg = ImageDescription.ServerImageDescription("./img/testimg.png")
}

def doAfterDom(): Unit = {
  println("--- do after dom!")
}


object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main
