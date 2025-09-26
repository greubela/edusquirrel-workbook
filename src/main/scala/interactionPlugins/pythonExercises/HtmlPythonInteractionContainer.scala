package interactionPlugins.pythonExercises

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionComponent.InteractionComponentForRole
import workbook.model.display.InteractionComponent.InteractionContentRole
import workbook.model.display.InteractionDisplayState
import workbook.model.feedback.scaffolding.BasicVariableScaffoldingResult
import workbook.model.states.Stateless
import workbook.workbookHtmlElements.abstractions.{HtmlFullInteractionContainer, HtmlFullInteractionExercise}

final class HtmlPythonInteractionContainer(
    val correspondingExercise: HtmlFullInteractionExercise[
      PythonEditorState,
      Stateless,
      PythonGradingState,
      BasicVariableScaffoldingResult[String, Stateless],
      PythonGradingResult,
      PythonScaffolder,
      PythonGrader
    ],
    val interactionModel: HtmlPythonInteractionModel
) extends HtmlFullInteractionContainer[
      PythonEditorState,
      Stateless,
      PythonGradingState,
      BasicVariableScaffoldingResult[String, Stateless],
      PythonGradingResult,
      PythonScaffolder,
      PythonGrader
    ] {

  private val pythonModel = interactionModel

  private val editorComponent = pythonModel.visualizer.visualizeEditor(pythonModel.model.currentEditorStateVar)
  private val gradingConfigComponent =
    pythonModel.visualizer.visualizeGraderStateEditor(pythonModel.model.currentGradingStateVar)
  private val scaffoldingResultComponent =
    pythonModel.visualizer.visualizeScaffoldingResult(pythonModel.model.currentScaffoldingResultVar)
  private val gradingResultComponent =
    pythonModel.visualizer.visualizeGradingResult(pythonModel.model.currentGradingResultVar)

  private val allComponents =
    List(editorComponent, gradingConfigComponent, scaffoldingResultComponent, gradingResultComponent)

  private val visibleRoles: List[InteractionContentRole] = allComponents.map(_.forContentRole)

  override val displayState: Var[InteractionDisplayState] = Var(
    InteractionDisplayState.CustomDisplayState(
      allKnownComponents = allComponents,
      visibleComponentRolesInOrder = visibleRoles,
      layoutCssForExercise = Seq("style-vbox", "python-exercise-layout")
    )
  )

  private val isRunning = Var(false)

  private val runButton = button(
    cls := "python-run-button",
    disabled <-- isRunning.signal,
    "Run tests",
    onClick --> { _ =>
      isRunning.set(true)
      pythonModel.model.currentGradingResultVar.set(None)
      pythonModel.controller.grader.gradeState(
        pythonModel.model.currentEditorStateVar.now(),
        result => {
          pythonModel.model.currentGradingResultVar.set(Some(result))
          pythonModel.model.currentGradingStateVar.set(result.stateWhenStarted)
          isRunning.set(false)
        }
      )
    }
  )

  private val domElement =
    div(
      cls := "python-interaction-container",
      div(
        cls := "python-editor-column",
        editorComponent.getDomElement(),
        div(cls := "python-editor-actions", runButton)
      ),
      div(
        cls := "python-side-column",
        gradingConfigComponent.getDomElement(),
        scaffoldingResultComponent.getDomElement(),
        gradingResultComponent.getDomElement()
      )
    )

  override def getDomElement(): L.Element = domElement
}
