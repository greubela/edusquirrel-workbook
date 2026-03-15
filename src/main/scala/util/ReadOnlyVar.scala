package util

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

trait ReadOnlyVar[T]() {

  def signal: Signal[T] 

  def now(): T 

  def map[O](func: T => O): ReadOnlyVar[O] = ReadOnlyVar(this, func)
  
}

object ReadOnlyVar {
  
  def apply[T](underlyingVariable: Var[T]): ReadOnlyVar[T] = new ReadOnlyVar[T]() {
    def signal: Signal[T] = underlyingVariable.signal

    def now(): T = underlyingVariable.now()    
  }
  
  def apply[I, O](readOnlyVar: ReadOnlyVar[I], mapFunc: I => O): ReadOnlyVar[O] = new ReadOnlyVar[O]() {
    def signal: Signal[O] = readOnlyVar.signal.map(mapFunc)

    def now(): O = mapFunc(readOnlyVar.now())
  }
  
}
