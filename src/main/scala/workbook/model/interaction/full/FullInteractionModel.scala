package workbook.model.interaction.full

import scala.collection.mutable
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.states.{FullInteractionState, InteractionState}

case class FullInteractionModel[EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState, S <: ScaffoldingResult, G <: GradingResult]
(initState: FullInteractionState[EditorState, ScaffoldingState, GradingState]) {

  private var listeners: mutable.ListBuffer[FullInteractionModel[EditorState, ScaffoldingState, GradingState, S, G] => Any] = mutable.ListBuffer()

  private var stateList = mutable.ListBuffer[FullInteractionState[EditorState, ScaffoldingState, GradingState]](initState)
  private var scaffoldingResults = mutable.ListBuffer[S]()
  private var gradingResults = mutable.ListBuffer[G]()

  def currentState: FullInteractionState[EditorState, ScaffoldingState, GradingState] = stateList.last

  def logNewInteractionState(interactionState: FullInteractionState[EditorState, ScaffoldingState, GradingState]): Unit = {
    stateList.append(interactionState)
    listeners.foreach(listener => listener(this))
  }

  def getStateHistory(): List[FullInteractionState[EditorState, ScaffoldingState, GradingState]] = stateList.toList


  def logNewScaffoldingResult(scaffoldingResult: S): Unit = {
    scaffoldingResults.append(scaffoldingResult)
    listeners.foreach(listener => listener(this))
  }

  def getScaffoldingResults(): List[S] = scaffoldingResults.toList


  def logNewGradingResult(gradingResult: G): Unit = {
    gradingResults.append(gradingResult)
    listeners.foreach(listener => listener(this))
  }

  def getGradingResults(): List[G] = gradingResults.toList


  def addListener(newListener: FullInteractionModel[EditorState, ScaffoldingState, GradingState, S, G] => Any): Unit = listeners.append(newListener)

}