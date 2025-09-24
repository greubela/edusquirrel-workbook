package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.InteractionContentRole.{Editor, GradingResult, GradingStateEditor, ScaffoldingResult, ScaffoldingStateEditor}
import workbook.model.display.InteractionComponent.InteractionWithRole
import workbook.model.interaction.full.{FullInteractionController, FullInteractionExerciseModel, FullInteractionVisualizer, HtmlFullInteractionModel}

class HtmlAutomatonInteractionModel(exerciseContent: AutomatonExerciseContent)
    extends HtmlFullInteractionModel[
      AutomatonEditorState,
      AutomatonScaffoldingState,
      AutomatonGradingState,
      AutomatonScaffoldingFeedback,
      AutomatonGradingFeedback,
      AutomatonScaffolder,
      AutomatonGrader
    ] {

  private val initialNode = AutomatonNode("q0", "q0", 220.0, 160.0, isStart = true, isAccepting = true)
  private val initialEditorState = AutomatonEditorState(exerciseContent.defaultMode, Vector(initialNode), Vector.empty)
  private val initialScaffoldingState = AutomatonScaffoldingState(initialEditorState)
  private val initialGradingState = AutomatonGradingState(initialEditorState, exerciseContent.shouldAccept, exerciseContent.shouldReject)

  override val model: FullInteractionExerciseModel[
    AutomatonEditorState,
    AutomatonScaffoldingState,
    AutomatonGradingState,
    AutomatonScaffoldingFeedback,
    AutomatonGradingFeedback
  ] = new FullInteractionExerciseModel(initialEditorState, initialScaffoldingState, initialGradingState)

  val scaffolder: AutomatonScaffolder = new AutomatonScaffolder
  val grader: AutomatonGrader = new AutomatonGrader
  val simulationController: AutomatonSimulationController = new AutomatonSimulationController(initialEditorState)

  val editorStore: AutomatonEditorStore = new AutomatonEditorStore(initialEditorState, state => syncState(state))

  override val controller: FullInteractionController[
    AutomatonEditorState,
    AutomatonScaffoldingState,
    AutomatonGradingState,
    AutomatonScaffoldingFeedback,
    AutomatonGradingFeedback,
    AutomatonScaffolder,
    AutomatonGrader
  ] = FullInteractionController(scaffolder, grader)

  private def syncState(state: AutomatonEditorState): Unit = {
    model.currentEditorStateVar.set(state)
    val scaffoldingState = AutomatonScaffoldingState(state)
    val gradingState = AutomatonGradingState(state, exerciseContent.shouldAccept, exerciseContent.shouldReject)
    model.currentScaffoldingStateVar.set(scaffoldingState)
    model.currentGradingStateVar.set(gradingState)
    model.currentScaffoldingResultVar.set(None)
    model.currentGradingResultVar.set(None)
    scaffolder.loadState(scaffoldingState)
    grader.loadState(gradingState)
    simulationController.onEditorStateChanged(state)
  }

  scaffolder.loadState(initialScaffoldingState)
  grader.loadState(initialGradingState)

  override val visualizer: FullInteractionVisualizer[
    AutomatonEditorState,
    AutomatonScaffoldingState,
    AutomatonGradingState,
    AutomatonScaffoldingFeedback,
    AutomatonGradingFeedback,
    AutomatonScaffolder,
    AutomatonGrader
  ] = new FullInteractionVisualizer[
    AutomatonEditorState,
    AutomatonScaffoldingState,
    AutomatonGradingState,
    AutomatonScaffoldingFeedback,
    AutomatonGradingFeedback,
    AutomatonScaffolder,
    AutomatonGrader
  ] {
    override def visualizeEditor(data: Var[AutomatonEditorState]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new HtmlAutomatonEditorInteractionComponent(editorStore), Editor)

    override def visualizeScaffolderStateEditor(data: Var[AutomatonScaffoldingState]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new AutomatonSimulationComponent(simulationController), ScaffoldingStateEditor)

    override def visualizeGraderStateEditor(data: Var[AutomatonGradingState]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new AutomatonExpectedWordsComponent(model.currentGradingStateVar), GradingStateEditor)

    override def visualizeScaffoldingResult(data: Var[Option[AutomatonScaffoldingFeedback]]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new AutomatonScaffoldingResultComponent(model.currentScaffoldingResultVar), ScaffoldingResult)

    override def visualizeGradingResult(data: Var[Option[AutomatonGradingFeedback]]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new AutomatonGradingResultComponent(model.currentGradingResultVar), GradingResult)
  }
}
