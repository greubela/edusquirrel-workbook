package workbook.model.interaction.history

import com.raquo.airstream.core.Observer
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.unsafeWindowOwner

import scala.collection.mutable
case class VarWithHistory[T](variable: Var[T]) {
  val stateList = mutable.ListBuffer[T]()
  
  variable.signal.addObserver(Observer[T](newValue => stateList.append(newValue)))(unsafeWindowOwner)
}

object VarWithHistory {
  
  
  
}