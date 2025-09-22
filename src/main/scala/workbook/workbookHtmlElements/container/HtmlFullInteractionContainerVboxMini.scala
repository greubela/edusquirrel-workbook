package workbook.workbookHtmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.exercise.ExerciseContent
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Editor, Grader, Scaffolder}
import workbook.model.interaction.full.{FullInteraction, FullInteractionController, FullInteractionModel, FullInteractionVisualizer}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.SvgFactory
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionContainer


case class HtmlFullInteractionContainerVboxMini[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult, GR <: GradingResult,
  E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
](
   exercise: ExerciseContent,
   model: FullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR],
   controller: FullInteractionController[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G],
   componentVisualizer: FullInteractionVisualizer[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]
 ) extends HtmlFullInteractionContainer[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G] {

  model.addListener(newState => println("state has changed and I realized it :D")) // todo

  private var curLayout: String = "layout-editor"

  private def editorContainer = div(cls := "container-interaction-element-content content-editor",
    componentVisualizer.visualizeEditor(model.currentState.editorState)
  )
  private def scaffoldingContainer = div(cls := "container-interaction-element-content content-scaffolding",
    componentVisualizer.visualizeScaffolderStateEditor(model.currentState.scaffoldingState),
    SvgFactory.createButtonArrowRight(event => {}, event => {}, event => {}),

  )


  private def gradingContainer = div(cls := "container-interaction-element-content content-grading",
    componentVisualizer.visualizeGraderStateEditor(model.currentState.gradingState)
  )
  private val fullExerciseElement =
    div(
      //cls := "container-interaction " + layoutClass + " " + highlightClass,
      cls := "container-interaction highlight-editor " + curLayout,

      // SCAFFOLDING
      SvgFactory.createButtonScaffolding(event => switchToLayout("layout-scaffolding"), event => switchToHighlight("highlight-scaffolding"), event => switchHighlightToLayout()),

      div(cls := "container-interaction-element interaction-scaffolding",
        div(cls := "container-label label-scaffolding",
          "HILFE !?!?!?"),
        scaffoldingContainer
      ),

      // EDITOR
      SvgFactory.createButtonEditor(event => switchToLayout("layout-editor"), event => switchToHighlight("highlight-editor"), event => switchHighlightToLayout()),

      div(cls := "container-interaction-element interaction-editor",
        div(cls := "container-label label-editor",
          "INSERT STUFF HERE OR SO I GUESS!=!="),
        editorContainer
      ),

      // ARROW BUTTONS
      div(cls := "container-instruction-arrows",
        SvgFactory.createButtonArrowDown(event => {}, event => {}, event => {}),
        SvgFactory.createButtonArrowRight(event => {}, event => {}, event => {}),
      ),

      SvgFactory.createButtonGrading(event => switchToLayout("layout-grading"), event => switchToHighlight("highlight-grading"), event => switchHighlightToLayout()),

      // GRADING
      div(cls := "container-interaction-element interaction-grading",
        div(cls := "container-label label-grading",
          "Ai RESULT OR SO I GUESS???"),
        gradingContainer
      )
    )


  override def getDomElement(): L.Element = fullExerciseElement

  private def switchToLayout(layoutStr: String): Unit = {
    print("switching to layout: " + layoutStr)
    val expectedLayouts = List("layout-editor", "layout-scaffolding", "layout-grading")
    if (!expectedLayouts.contains(layoutStr)) {
      print("[WARN] unexpected layout in FullTextBasedExerciseElement: " + layoutStr + ", expected one of: " + expectedLayouts.mkString(", "))
    }
    expectedLayouts.foreach(curLayout => fullExerciseElement.ref.classList.remove(curLayout))
    fullExerciseElement.ref.classList.add(layoutStr)
    curLayout = layoutStr
  }

  private def switchToHighlight(highlightStr: String): Unit = {
    print("switching to highlight: " + highlightStr)
    val expectedHighlights = List("highlight-editor", "highlight-scaffolding", "highlight-grading")
    if (!expectedHighlights.contains(highlightStr)) {
      print("[WARN] unexpected highlight in FullTextBasedExerciseElement: " + highlightStr + ", expected one of: " + expectedHighlights.mkString(", "))
    }
    expectedHighlights.foreach(curHighlight => fullExerciseElement.ref.classList.remove(curHighlight))
    fullExerciseElement.ref.classList.add(highlightStr)
  }

  private def switchHighlightToLayout(): Unit = {
    switchToHighlight("highlight-" + curLayout.split("-").last)
  }


  override def notifyOnModelUpdate(): Unit = {
    println("not implemented yet: notifyOnModelUpdate")
  }

  override def enableInteraction(): Unit = {

    println("not implemented yet: enableInteraction")
  }

  override def disableInteraction(): Unit = {

    println("not implemented yet: disableInteraction")
  }

}


/*

class FullTextBasedExerciseElement
(exerciseId: String, editor: TextBasedEditor, scaffolder: TextBasedScaffolder, grader: TextBasedGrader)
  extends HtmlFullExerciseElement[BasicStringState, BasicStringFeedback, BasicStringGrade] {



  //createDomElement()

  private var lastScaffoldingResult: Option[BasicStringFeedback] = None
  private var lastGradingResult: Option[BasicStringGrade] = None

  override def getGrader(): Grader[BasicStringState, BasicStringGrade] = grader

  override def getScaffolder(): Scaffolder[BasicStringState, BasicStringFeedback] = scaffolder

  override def onEditorStateChanged(oldState: Option[BasicStringState], newState: BasicStringState): Unit = {
    println("editor state changed!")
  }

  override def getInitialState(): BasicStringState = BasicStringState.empty



  private def buttonPressed(classStr: String, mouseEvent: MouseEvent): Unit = {
    println("event: " + mouseEvent + ", classStr: " + classStr)
  }

}
*/

object HtmlFullInteractionContainerVboxMini {


  def add[A <: String](asdf: Int): String = ""

  def apply[
    EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState, SR <: ScaffoldingResult, GR <: GradingResult, E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
  ](exercise: ExerciseContent, fullInteraction: FullInteraction[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]):
  HtmlFullInteractionContainerVboxMini[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G] =
    HtmlFullInteractionContainerVboxMini(exercise, fullInteraction.model, fullInteraction.controller, fullInteraction.visualizer)

}

