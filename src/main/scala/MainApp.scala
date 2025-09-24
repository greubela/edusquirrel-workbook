
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.AppLanguage
import interactionPlugins.gpt.HtmlTextBasedGptExercise
import interactionPlugins.turtleEnvironment.{HtmlTurtleExercise, TurtleCommand, TurtleExerciseContent, TurtleExpression, TurtleProgramState}
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.model.exercise.ExerciseContent

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

  val turtleSampleProgram = TurtleProgramState(
    List(
      TurtleCommand.WhenProgramStarted,
      TurtleCommand.PenDown,
      TurtleCommand.Repeat(TurtleExpression.Literal(4)),
      TurtleCommand.Forward(TurtleExpression.Literal(100)),
      TurtleCommand.TurnRight(TurtleExpression.Literal(90)),
      TurtleCommand.EndRepeat,
      TurtleCommand.PenUp
    )
  )

  val turtleTargetSvg =
    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
      |  <g stroke="#0c3359" stroke-width="6" fill="none" stroke-linecap="round">
      |    <path d="M60 60 L180 60 L180 180 L60 180 Z"/>
      |  </g>
      |</svg>""".stripMargin

  val turtleExContent = TurtleExerciseContent(
    id = "turtle-001",
    titleMap = Map(AppLanguage.English -> "Draw a square"),
    instructionMap = Map(AppLanguage.English -> "Use the turtle blocks to draw a square with side length 100."),
    targetSvg = turtleTargetSvg,
    sampleProgram = turtleSampleProgram,
    targetDescription = Some("The figure is a square stitched with four equal sides.")
  )
  val htmlTurtleEx = new HtmlTurtleExercise(turtleExContent)

  val worksheetDiv = document.getElementById("worksheetDts")

  val combinedElement = div(
    htmlEx.getDomElement(),
    htmlTurtleEx.getDomElement()
  )

  if (dom.document.readyState == "loading") {
    renderOnDomContentLoaded(worksheetDiv, combinedElement)
  } else {
    render(worksheetDiv, combinedElement)
  }
}


def insertWorkbookOld(): Unit = {

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
