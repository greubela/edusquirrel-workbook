package interactionPlugins.pythonExercises

import com.raquo.airstream.state.Var
import workbook.model.display.InteractionComponent.InteractionContentRole
import workbook.model.display.InteractionComponent.InteractionContentRole.*
import workbook.model.display.InteractionComponent.{InteractionComponentForRole, InteractionWithRole}
import workbook.model.feedback.scaffolding.BasicVariableScaffoldingResult
import workbook.model.interaction.full.{FullInteractionController, FullInteractionExerciseModel, FullInteractionVisualizer}
import workbook.model.interaction.full.HtmlFullInteractionModel
import workbook.model.states.Stateless

final class HtmlPythonInteractionModel(exerciseContent: PythonExerciseContent) extends HtmlFullInteractionModel[
  PythonEditorState,
  Stateless,
  PythonGradingState,
  BasicVariableScaffoldingResult[String, Stateless],
  PythonGradingResult,
  PythonScaffolder,
  PythonGrader
] {

  private val initEditorState = PythonEditorState(exerciseContent.starterCode)
  private val initGradingState = PythonGradingState(exerciseContent.starterCode)

  val model = new FullInteractionExerciseModel[
    PythonEditorState,
    Stateless,
    PythonGradingState,
    BasicVariableScaffoldingResult[String, Stateless],
    PythonGradingResult
  ](initEditorState, Stateless.StatelessInstance, initGradingState)

  val controller = FullInteractionController(PythonScaffolder(), PythonGrader(exerciseContent))

  val visualizer = new FullInteractionVisualizer[
    PythonEditorState,
    Stateless,
    PythonGradingState,
    BasicVariableScaffoldingResult[String, Stateless],
    PythonGradingResult,
    PythonScaffolder,
    PythonGrader
  ] {

    override def visualizeEditor(data: Var[PythonEditorState]): InteractionComponentForRole =
      InteractionWithRole(new PythonCodeEditorComponent(data), Editor)

    override def visualizeScaffolderStateEditor(data: Var[Stateless]): InteractionComponentForRole =
      InteractionWithRole(new PythonScaffoldingStateComponent, ScaffoldingStateEditor)

    override def visualizeGraderStateEditor(data: Var[PythonGradingState]): InteractionComponentForRole =
      InteractionWithRole(new PythonGradingConfigComponent(exerciseContent, data), GradingStateEditor)

    override def visualizeScaffoldingResult(
        data: Var[Option[BasicVariableScaffoldingResult[String, Stateless]]]
    ): InteractionComponentForRole =
      InteractionWithRole(new PythonScaffoldingResultComponent(data), ScaffoldingResult)

    override def visualizeGradingResult(data: Var[Option[PythonGradingResult]]): InteractionComponentForRole =
      InteractionWithRole(new PythonResultComponent(data), GradingResult)
  }
}
