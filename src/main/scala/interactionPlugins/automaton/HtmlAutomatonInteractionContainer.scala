package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import workbook.model.display.InteractionDisplayState
import workbook.model.display.InteractionDisplayState.DefaultEditorDisplayState
import workbook.workbookHtmlElements.HtmlPlaintextInstructionElement
import workbook.workbookHtmlElements.abstractions.{HtmlFullInteractionContainer, HtmlFullInteractionExercise}

case class HtmlAutomatonInteractionContainer(
  correspondingExercise: HtmlAutomatonExercise,
  interactionModel: HtmlAutomatonInteractionModel
) extends HtmlFullInteractionContainer[
      AutomatonEditorState,
      AutomatonScaffoldingState,
      AutomatonGradingState,
      AutomatonScaffoldingFeedback,
      AutomatonGradingFeedback,
      AutomatonScaffolder,
      AutomatonGrader
    ] {

  private val editorComponent = interactionModel.visualizer.visualizeEditor(interactionModel.model.currentEditorStateVar)
  private val simulationComponent = interactionModel.visualizer.visualizeScaffolderStateEditor(interactionModel.model.currentScaffoldingStateVar)
  private val scaffoldingResultComponent = interactionModel.visualizer.visualizeScaffoldingResult(interactionModel.model.currentScaffoldingResultVar)
  private val gradingStateComponent = interactionModel.visualizer.visualizeGraderStateEditor(interactionModel.model.currentGradingStateVar)
  private val gradingResultComponent = interactionModel.visualizer.visualizeGradingResult(interactionModel.model.currentGradingResultVar)

  private val instructionElement = HtmlPlaintextInstructionElement(correspondingExercise.exerciseContent.instructionMap)

  override val displayState: Var[InteractionDisplayState] = Var(DefaultEditorDisplayState(List()))

  private val runGuidanceButton = button(
    cls := "turtle-action-button",
    "Analyze automaton",
    onClick --> (_ =>
      interactionModel.controller.scaffolder.generateFeedback(result => interactionModel.model.currentScaffoldingResultVar.set(Some(result)))
    )
  )

  private val runTestsButton = button(
    cls := "turtle-action-button",
    "Run tests",
    onClick --> (_ =>
      interactionModel.controller.grader.gradeState(
        interactionModel.model.currentEditorStateVar.now(),
        result => interactionModel.model.currentGradingResultVar.set(Some(result))
      )
    )
  )

  private val domElement =
    div(
      AutomatonStyles.styles,
      cls := "turtle-full-exercise automaton-exercise",
      div(
        cls := "turtle-column editor-control",
        h3("Editor"),
        editorComponent.getDomElement()
      ),
      div(
        cls := "turtle-column", // simulation column
        h3("Simulator"),
        simulationComponent.getDomElement()
      ),
      div(
        cls := "turtle-column instruction",
        h3("Instructions"),
        instructionElement.getDomElement(),
        h3("Guidance"),
        runGuidanceButton,
        scaffoldingResultComponent.getDomElement(),
        h3("Test specification"),
        gradingStateComponent.getDomElement(),
        div(cls := "turtle-action-row", runTestsButton),
        gradingResultComponent.getDomElement()
      )
    )

  override def getDomElement(): L.Element = domElement
}
