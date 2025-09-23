package workbook.model.interaction.full

import com.raquo.airstream.core.Observer
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom.svg.G
import util.VarWithHistory
import workbook.model.display.InteractionComponent
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.states.BasicVariableBasedState.*
import workbook.model.states.{BasicVariableBasedState, FullInteractionState, InteractionState}

import scala.collection.mutable

case class FullInteractionExerciseModel[EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState, SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState]]
(initEditorState: EditorState, initScaffoldingState: ScaffoldingState, initGradingState: GradingState) {
  

  private val editorStateVarWithHistory = VarWithHistory(Var(initEditorState))

  def currentEditorStateVar: Var[EditorState] = editorStateVarWithHistory.variable

  private val scaffoldingStateVarWithHistory = VarWithHistory(Var(initScaffoldingState))

  def currentScaffoldingStateVar: Var[ScaffoldingState] = scaffoldingStateVarWithHistory.variable

  private val gradingStateVarWithHistory = VarWithHistory(Var(initGradingState))

  def currentGradingStateVar: Var[GradingState] = gradingStateVarWithHistory.variable

  private val scaffoldingResultVarWithHistory = VarWithHistory(Var[Option[SR]](None))

  def currentScaffoldingResultVar: Var[Option[SR]] = scaffoldingResultVarWithHistory.variable

  private val gradingResultVarWithHistory = VarWithHistory(Var[Option[GR]](None))

  def currentGradingResultVar: Var[Option[GR]] = gradingResultVarWithHistory.variable

}