package scala.interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.InteractionContentRole
import workbook.model.display.InteractionComponent.InteractionContentRole.*
import workbook.model.display.InteractionComponent.InteractionWithRole
import workbook.model.interaction.full.{FullInteractionController, FullInteractionExerciseModel, FullInteractionVisualizer, HtmlFullInteractionModel}

class HtmlTurtleInteractionModel(exerciseContent: TurtleExerciseContent) extends HtmlFullInteractionModel[
  TurtleEditorState,
  TurtleScaffoldingState,
  TurtleGradingState,
  TurtleScaffoldingFeedback,
  TurtleGradingFeedback,
  TurtleScaffolder,
  TurtleGrader
] {

  private val sampleProgram = exerciseContent.sampleProgram
  private val expectedExecution = TurtleProgramExecutor.execute(sampleProgram)

  private val initialBlock = TurtleBlockLibrary.whenProgramStarted.createInstance()

  private val initEditorState = TurtleEditorState(TurtleProgramState.fromBlocks(List(initialBlock)))
  private val initScaffoldingState = TurtleScaffoldingState(initEditorState.program, sampleProgram)
  private val initGradingState = TurtleGradingState(initEditorState.program, expectedExecution.lines)

  val model = new FullInteractionExerciseModel[TurtleEditorState, TurtleScaffoldingState, TurtleGradingState, TurtleScaffoldingFeedback, TurtleGradingFeedback](
    initEditorState,
    initScaffoldingState,
    initGradingState
  )

  val executionResultVar: Var[Option[TurtleExecutionResult]] = Var(None)
  val dragContext = new TurtleBlockDragContext

  val scaffolder = new TurtleScaffolder(sampleProgram)
  val grader = new TurtleGrader(expectedExecution.lines)

  val blockProgram = new TurtleBlockProgram(List(initialBlock), programState => {
    val editorState = TurtleEditorState(programState)
    model.currentEditorStateVar.set(editorState)
    val scaffoldingState = TurtleScaffoldingState(programState, sampleProgram)
    model.currentScaffoldingStateVar.set(scaffoldingState)
    val gradingState = TurtleGradingState(programState, expectedExecution.lines)
    model.currentGradingStateVar.set(gradingState)
    scaffolder.loadState(scaffoldingState)
    grader.loadState(gradingState)
    executionResultVar.set(None)
  })

  val controller = FullInteractionController(scaffolder, grader)
  scaffolder.loadState(initScaffoldingState)
  grader.loadState(initGradingState)

  val visualizer: FullInteractionVisualizer[
    TurtleEditorState,
    TurtleScaffoldingState,
    TurtleGradingState,
    TurtleScaffoldingFeedback,
    TurtleGradingFeedback,
    TurtleScaffolder,
    TurtleGrader
  ] = new FullInteractionVisualizer[
    TurtleEditorState,
    TurtleScaffoldingState,
    TurtleGradingState,
    TurtleScaffoldingFeedback,
    TurtleGradingFeedback,
    TurtleScaffolder,
    TurtleGrader
  ] {
    override def visualizeEditor(data: Var[TurtleEditorState]): InteractionComponent.InteractionComponentForRole = {
      val editor = new TurtleEditorInteractionComponent(blockProgram, dragContext, executionResultVar)
      InteractionWithRole(editor, Editor)
    }

    override def visualizeScaffolderStateEditor(data: Var[TurtleScaffoldingState]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new TurtleScaffoldingStateComponent(model.currentScaffoldingStateVar), ScaffoldingStateEditor)

    override def visualizeGraderStateEditor(data: Var[TurtleGradingState]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new TurtleTargetPreviewComponent(exerciseContent.targetSvg, exerciseContent.targetDescription), GradingStateEditor)

    override def visualizeScaffoldingResult(data: Var[Option[TurtleScaffoldingFeedback]]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new TurtleScaffoldingResultComponent(model.currentScaffoldingResultVar), ScaffoldingResult)

    override def visualizeGradingResult(data: Var[Option[TurtleGradingFeedback]]): InteractionComponent.InteractionComponentForRole =
      InteractionWithRole(new TurtleGradingResultComponent(model.currentGradingResultVar), GradingResult)
  }
}
