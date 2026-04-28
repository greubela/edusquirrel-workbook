package it.evadid.core.datastructures.state

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.*


object StateHelper {

  implicit class StateBasedVar[T](state: State[T]) {
    def toAirstreamVar: Var[T] = fromStateToAirstreamVar(state)
  }

  implicit class VarBasedState[T](airstreamVar: Var[T]) {
    def toState: State[T] = fromAirstreamVarToState(airstreamVar)
  }

  def fromStateToAirstreamVar[T](state: State[T]): Var[T] = {
    val airstreamVar = Var(state.now())
    bindTogether(state, airstreamVar)
    airstreamVar
  }

  def fromAirstreamVarToState[T](airstreamVar: Var[T]): State[T] = {
    val state = new State[T](airstreamVar.now())
    bindTogether(state, airstreamVar)
    state
  }

  def bindTogether[T](state: State[T], airstreamVar: Var[T]): Unit = {
    state.addObserver(nextValue => airstreamVar.set(nextValue))
    airstreamVar.signal.distinct.foreach(state.set)(unsafeWindowOwner)
  }


}
