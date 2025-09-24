package scala.interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionDisplayState
import workbook.model.display.InteractionDisplayState.DefaultEditorDisplayState
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.full.HtmlFullInteractionModel
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.HtmlPlaintextInstructionElement
import workbook.workbookHtmlElements.abstractions.{HtmlFullInteractionContainer, HtmlFullInteractionExercise}

class HtmlTurtleInteractionContainer[
  EditorState <: InteractionState,
  ScaffoldingState <: InteractionState,
  GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState],
  GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR],
  G <: Grader[EditorState, GradingState, GR]
](
  val correspondingExercise: HtmlFullInteractionExercise[EditorState, ScaffoldingState, GradingState, SR, GR, S, G],
  val interactionModel: HtmlFullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR, S, G]
) extends HtmlFullInteractionContainer[EditorState, ScaffoldingState, GradingState, SR, GR, S, G] {

  private val turtleModel = interactionModel.asInstanceOf[HtmlTurtleInteractionModel]

  private val editorComponent = interactionModel.visualizer.visualizeEditor(turtleModel.model.currentEditorStateVar)
  private val scaffoldingResultComponent = interactionModel.visualizer.visualizeScaffoldingResult(turtleModel.model.currentScaffoldingResultVar)
  private val gradingResultComponent = interactionModel.visualizer.visualizeGradingResult(turtleModel.model.currentGradingResultVar)
  private val targetPreviewComponent = interactionModel.visualizer.visualizeGraderStateEditor(turtleModel.model.currentGradingStateVar)
  private val scaffoldingStateComponent = interactionModel.visualizer.visualizeScaffolderStateEditor(turtleModel.model.currentScaffoldingStateVar)

  private val palette = new HtmlBlockDragFromArea(
    turtleModel.dragContext,
    TurtleBlockLibrary.blocksByCategory,
    definition => {
      val newBlocks = TurtleBlockLibrary.instantiateWithCompanion(definition)
      turtleModel.blockProgram.insertBlocks(turtleModel.blockProgram.currentBlocks.length, newBlocks)
    }
  )

  private val instructionElement = HtmlPlaintextInstructionElement(correspondingExercise.exerciseContent.instructionMap)

  override val displayState: Var[InteractionDisplayState] = Var(DefaultEditorDisplayState(List()))

  private val runScaffoldingButton = button(
    cls := "turtle-action-button",
    "Check differences",
    onClick --> (_ =>
      turtleModel.controller.scaffolder.generateFeedback(result => turtleModel.model.currentScaffoldingResultVar.set(Some(result)))
    )
  )

  private val runGradingButton = button(
    cls := "turtle-action-button",
    "Grade solution",
    onClick --> (_ =>
      turtleModel.controller.grader.gradeState(
        turtleModel.model.currentEditorStateVar.now(),
        result => turtleModel.model.currentGradingResultVar.set(Some(result))
      )
    )
  )

  private val domElement =
    div(
      cls := "turtle-full-exercise",
      div(
        cls := "turtle-column tab-pane",
        h3("Blocks"),
        palette.getDomElement()
      ),
      div(
        cls := "turtle-column editor-control",
        h3("Editor"),
        editorComponent.getDomElement(),
        div(cls := "turtle-action-row", runScaffoldingButton, runGradingButton)
      ),
      div(
        cls := "turtle-column instruction",
        h3("Instructions"),
        instructionElement.getDomElement(),
        h3("Target figure"),
        targetPreviewComponent.getDomElement(),
        h3("Program guidance"),
        scaffoldingStateComponent.getDomElement(),
        scaffoldingResultComponent.getDomElement(),
        gradingResultComponent.getDomElement()
      )
    )

  override def getDomElement(): L.Element = domElement
}
