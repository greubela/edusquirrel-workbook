
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
import util.JSXGraph.*
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

def jsxGraphPreview: HtmlElement =
  given Fraction[Double] = summon[Fractional[Double]]
  given JsValueConverter[Double] = JsValueConverter.defaultConverter[Double]

  val boardId = "jsxgraph-demo-board"
  val jsxFacade = JsxGraphFacade[Double]()
  val radiusVar = Var(2.5)

  case class DemoState(board: JsxBoard[Double], center: JsxPoint[Double], through: JsxPoint[Double])

  val demoState = Var(Option.empty[DemoState])

  def buildBoard(radius: Double): Unit =
    demoState.now().foreach(_.board.free())
    Option(document.getElementById(boardId)).foreach(_.innerHTML = "")

    val board = jsxFacade.initBoard(boardId, BoardOptions[Double](boundingBox = Some(BoundingBox(-6.0, 6.0, 6.0, -6.0)), grid = true))
    val center = board.createPoint(Coordinate(0.0, 0.0), PointAttributes(CommonAttributes(name = Some("C"), fillColor = Some("#0ea5e9"), size = Some(4.0))))
    val through = board.createPoint(Coordinate(radius, 0.0), PointAttributes(CommonAttributes(name = Some("R"), fillColor = Some("#38bdf8"), size = Some(3.5))))
    board.createCircle(center, through, CircleAttributes(CommonAttributes(name = Some("Demo"), strokeColor = Some("#2563eb"))))

    demoState.set(Some(DemoState(board, center, through)))

  def clearBoard(): Unit =
    demoState.now().foreach(_.board.free())
    demoState.set(None)
    Option(document.getElementById(boardId)).foreach(_.innerHTML = "")

  div(
    h2("JSXGraph Preview"),
    p("Add a center point with a surrounding circle and adjust its radius interactively."),
    div(
      cls := "jsxgraph-demo-controls",
      button(
        "Create circle demo",
        onClick --> (_ => buildBoard(radiusVar.now()))
      ),
      button(
        "Reset demo",
        onClick --> (_ => clearBoard())
      )
    ),
    div(
      cls := "jsxgraph-slider-row",
      label("Radius: "),
      input(
        typ := "range",
        minAttr := "0.5",
        maxAttr := "8",
        stepAttr := "0.1",
        value <-- radiusVar.signal.map(_.toString),
        disabled <-- demoState.signal.map(_.isEmpty),
        onInput.mapToValue.map(value => value.toDoubleOption.getOrElse(radiusVar.now())) --> { value =>
          radiusVar.set(value)
          demoState.now().foreach { state =>
            state.through.moveTo(Coordinate(value, 0.0))
            state.board.fullUpdate()
          }
        }
      ),
      span(child.text <-- radiusVar.signal.map(r => f"${r}%.1f"))
    ),
    div(
      idAttr := boardId,
      cls := "jsxgraph-demo-board",
      styleAttr := "width: 520px; height: 360px; border: 1px solid #d1d5db; border-radius: 8px; margin-top: 0.5rem;"
    )
  )


def insertWorkbook(): Unit = {


  // Generic GPT Exercise
  val testEx = TextBasedGptExercise("id-007", Map(AppLanguage.English -> "this is title"), Map(AppLanguage.English -> "this is instruction"))
  val htmlEx = HtmlTextBasedGptExercise(testEx, useFullscreenInteraction = true, fullscreenElement = Some(fullscreenElement))

  // Programming Exercise
  val testProgEx = ProgrammingExercise(
    "id-003",
    Map(AppLanguage.English -> "Exercise 2"),
    Map(AppLanguage.English -> "Use Turtle Commands to program the Shape on the right :-)"),
    ProgrammingExercise.DefaultPentagonExpectedResult
  )
  val htmlProgEx = HtmlProgrammingExercise(testProgEx, fullscreenElement)

  val combinedElement = div(
    jsxGraphPreview,
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
