
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.vm.parsing.python.PythonParser
import interactionPlugins.automaton.{AutomatonExerciseContent, HtmlAutomatonExercise}
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig}
import interactionPlugins.blockEnvironment.exercise.{HtmlProgrammingExercise, ProgrammingExercise}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import interactionPlugins.gpt.{HtmlTextBasedGptExercise, TextBasedGptExercise}
import interactionPlugins.pythonExercises.{HtmlPythonExercise, PythonExerciseContent}
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.model.exercise.{ExerciseContent, ExerciseSection}
import workbook.workbookHtmlElements.container.HtmlFullScreenElement
import workbook.workbookHtmlElements.visualization.HtmlWorkbookOverview

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

@main
def mainApp(): Unit = {
  doSomeCalculations()
  insertWorkbook()
  //insertTurtleEditor()
}

def doSomeCalculations(): Unit = {
  /*
  val expr = BeProgram.debugGraphicsProgram()
  val ed = EditorState.withInitExpression(expr.fullProgram)
  val lis = BeTreeControllerConfig.editTreeConfig(ed)
  HtmlBeTreeDisplay.render(expr,
    ed.editorTreeDisplayConfig.now(),
    ed.rendererConfigVar.now(),
    lis,
    ed.controllerStateVar)
*/
}

val fullscreenElement: HtmlFullScreenElement = HtmlFullScreenElement()

def insertTurtleEditor(): Unit = {

  val editorDom = new HtmlFullscreenTurtleEditorElement(BeProgram.miniProgramExpression()).getDomElement()

  val worksheetDiv = document.getElementById("worksheetDts")

  if (dom.document.readyState == "loading") {
    renderOnDomContentLoaded(worksheetDiv, editorDom)
  } else {
    render(worksheetDiv, editorDom)
  }

}


def insertWorkbook(): Unit = {


  // Generic GPT Exercise
  val testEx = TextBasedGptExercise("id-007", Map(AppLanguage.English -> "this is title"), Map(AppLanguage.English -> "this is instruction"))
  val htmlEx = HtmlTextBasedGptExercise(testEx)

  // Programming Exercise
  val testProgEx = ProgrammingExercise(
    "id-003",
    Map(AppLanguage.English -> "Exercise 2"),
    Map(AppLanguage.English -> "Use Turtle Commands to program the Shape on the right :-)"),
    ProgrammingExercise.DefaultPentagonExpectedResult
  )
  val htmlProgEx = HtmlProgrammingExercise(testProgEx, fullscreenElement)

  val combinedElement = div(
    fullscreenElement.getDomElement(),
    /* div(
       h2("Workbook Overview"),
       div(
         cls := "workbook-overview-sample",
        // overviewElement
       )
     ),*/
    htmlEx.getDomElement(),
    htmlProgEx.getDomElement(),
    // automatonExercise.getDomElement(),
    // helloWorldExercise.getDomElement(),
    // fizzBuzzExercise.getDomElement()
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
