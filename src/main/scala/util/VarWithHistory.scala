package util

import com.raquo.airstream.core.Observer
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom.svg.G
import workbook.model.display.InteractionComponent
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.states.BasicVariableBasedState.*
import workbook.model.states.{BasicVariableBasedState, FullInteractionState, InteractionState}

import scala.collection.mutable


case class VarWithHistory[T](variable: Var[T]) {
  val stateList = mutable.ListBuffer[T]()
  variable.signal.addObserver(Observer[T](newValue => stateList.append(newValue)))(unsafeWindowOwner)
}