package it.evadid.core.datastructures.state

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.*
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.sync.UpdateImportance.TEMPORARY
import it.evadid.workbook.model.interaction.variable.InteractionVariable

object StateHelper {

  implicit class StateBasedVar[T](state: State[T]) {
    def toAirstreamVar: Var[T] = fromStateToAirstreamVar(state)
  }

  implicit class VarBasedState[T](airstreamVar: Var[T]) {
    def toState: State[T] = fromAirstreamVarToState(airstreamVar)
  }

  implicit class InteractionVariableOnJS[T](interactionVariable: InteractionVariable[T]) {

    def createBoundVarWithUpdateImportance(updateImportance: UpdateImportance): Var[T] = {
      val state = interactionVariable.createBoundStateWithUpdateImportance(updateImportance)
      fromStateToAirstreamVar(state)
    }

    def createInteractionSignal(): StrictSignal[T] = {
     // val res = Var[T](interactionVariable.currentValue)
      //interactionVariable.observableValue.addObserver(newValue => res.set(newValue))
      //res.signal

      createBoundVarWithUpdateImportance(TEMPORARY).signal
    }

  }

  def fromStateToAirstreamVar[T](state: State[T]): Var[T] = {
    val airstreamVar = Var(state.now())
    bindTogether(state, airstreamVar)
    airstreamVar
  }

  def fromAirstreamVarToState[T](airstreamVar: Var[T]): State[T] = {
    val state = State[T](airstreamVar.now())
    bindTogether(state, airstreamVar)
    state
  }

  def bindTogether[T](state: State[T], airstreamVar: Var[T]): Unit = {
    state.observable.addObserver(nextValue => airstreamVar.set(nextValue))
    airstreamVar.signal.distinct.foreach(state.set)(using unsafeWindowOwner)
  }


}
