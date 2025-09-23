
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.AppLanguage
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.model.exercise.ExerciseContent
import workbook.workbookHtmlElements.exercises.HtmlTextBasedGptExercise

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.language.postfixOps

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

@main
def mainApp(): Unit = {

  doOnStartup()

  insertWorkbook()
}

def insertWorkbook(): Unit = {

  val testEx = ExerciseContent("id-007", Map(AppLanguage.English -> "this is title"), Map(AppLanguage.English -> "this is instruction"))
  val htmlEx = HtmlTextBasedGptExercise(testEx)

  val worksheetDiv = document.getElementById("worksheetDts")

  if (dom.document.readyState == "loading") {
    renderOnDomContentLoaded(worksheetDiv, htmlEx.getDomElement())
  } else {
    render(worksheetDiv, htmlEx.getDomElement())
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
