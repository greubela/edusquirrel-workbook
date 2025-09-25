
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.language.AppLanguage
import interactionPlugins.automaton.{AutomatonExerciseContent, HtmlAutomatonExercise}
import interactionPlugins.gpt.{HtmlTextBasedGptExercise, TextBasedGptExercise}
import interactionPlugins.pythonExercises.{HtmlPythonExercise, PythonExerciseContent}
import interactionPlugins.turtleEnvironment.{HtmlTurtleExercise, TurtleCommand, TurtleExerciseContent, TurtleExpression, TurtleProgramState}
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.model.exercise.{ExerciseContent, ExerciseSection}
import workbook.workbookHtmlElements.visualization.HtmlWorkbookOverview

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

@main
def mainApp(): Unit = {

  insertWorkbook()
}

def insertWorkbook(): Unit = {

  // Generic GPT Exercise
  val testEx = TextBasedGptExercise("id-007", Map(AppLanguage.English -> "this is title"), Map(AppLanguage.English -> "this is instruction"))
  val htmlEx = HtmlTextBasedGptExercise(testEx)

  //  Turtle Exercise
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

  // Automaton exercise
  val automatonExercise = new HtmlAutomatonExercise(AutomatonExerciseContent.divisibleByThree)
  
  val overviewElement = new HtmlWorkbookOverview(sampleSections).getDomElement()
  val helloWorldExercise = new HtmlPythonExercise(PythonExerciseContent.helloWorld)
  val fizzBuzzExercise = new HtmlPythonExercise(PythonExerciseContent.fizzBuzz)

  val combinedElement = div(
    div(
      h2("Workbook Overview"),
      div(
        cls := "workbook-overview-sample",
        overviewElement
      )
    ),
    htmlEx.getDomElement(),
    htmlTurtleEx.getDomElement(),
    automatonExercise.getDomElement(),
    helloWorldExercise.getDomElement(),
    fizzBuzzExercise.getDomElement()
  )

  val worksheetDiv = document.getElementById("worksheetDts")

  if (dom.document.readyState == "loading") {
    renderOnDomContentLoaded(worksheetDiv, combinedElement)
  } else {
    render(worksheetDiv, combinedElement)
  }
}

object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main

final case class SimpleExercise(
    id: String,
    englishTitle: String,
    duration: Double,
    instruction: String
) extends ExerciseContent {
  override def titleMap: Map[AppLanguage, String] = Map(AppLanguage.English -> englishTitle)

  override def estimatedTimeInMinutes: Double = duration

  override def instructionMap: Map[AppLanguage, String] = Map(AppLanguage.English -> instruction)
}

final case class SampleSection(
    override val title: String,
    override val exercies: List[ExerciseContent],
    override val sectionsRequiredBefore: List[ExerciseSection] = Nil,
    override val sectionsRecommendedBefore: List[ExerciseSection] = Nil
) extends ExerciseSection

def sampleSections: List[ExerciseSection] = {
  val introExercises = List(
    SimpleExercise("intro-1", "Getting Started", 3, "Read the welcome material."),
    SimpleExercise("intro-2", "First Steps", 5, "Complete the introductory quiz."),
    SimpleExercise("intro-3", "Warmup", 2, "Review the glossary.")
  )
  val basicsExercises = List(
    SimpleExercise("basic-1", "Core Concepts", 8, "Work through the foundational lesson."),
    SimpleExercise("basic-2", "Examples", 6, "Study the worked examples.")
  )
  val practiceExercises = List(
    SimpleExercise("practice-1", "Drills", 4, "Solve the practice problems."),
    SimpleExercise("practice-2", "Challenge", 9, "Attempt the challenge exercise."),
    SimpleExercise("practice-3", "Reflection", 3, "Write a short reflection.")
  )
  val extensionExercises = List(
    SimpleExercise("extension-1", "Project Setup", 7, "Prepare the project workspace."),
    SimpleExercise("extension-2", "Project Build", 10, "Implement the project milestone."),
    SimpleExercise("extension-3", "Review", 5, "Conduct a peer review.")
  )

  val sectionA = SampleSection("Orientation", introExercises)
  val sectionB = SampleSection("Fundamentals", basicsExercises, sectionsRequiredBefore = List(sectionA))
  val sectionC = SampleSection("Practice", practiceExercises, sectionsRequiredBefore = List(sectionB))
  val sectionD = SampleSection(
    "Project",
    extensionExercises,
    sectionsRequiredBefore = List(sectionB),
    sectionsRecommendedBefore = List(sectionA)
  )

  List(sectionA, sectionB, sectionC, sectionD)
}
