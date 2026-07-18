package it.evadid.evacuation.core.datastructures.utility

import it.evadid.evacuation.core.datastructures.utility.ObservableVar._

import scala.collection.mutable.ListBuffer

case class ObservableVar[T](initValue: T = null) {

  private var value: T = initValue

  private val initListener = new ListBuffer[ValueInitedListener[T]]()
  private val changeListener = new ListBuffer[ValueChangedListener[T]]()

  def setValue(newValue: T): Unit = {
    assert(newValue != null, "Cannot set ObservableVar to null!")
    val oldValue = value
    value = newValue

    if (oldValue == null) initListener.foreach(_.apply(newValue))
    else changeListener.foreach(_.apply(oldValue, newValue))
  }

  def currentValue: T = if (value == null) {
    val ex =  new IllegalStateException("Accessing observable var before init!")
    ex.printStackTrace()
    throw ex
  } else value


  def addInitListener(newListener: ValueInitedListener[T], callIfAlreadyInited: Boolean = true): Unit = {
    initListener += newListener
    if (callIfAlreadyInited && value != null)
      newListener.apply(currentValue)
  }

  def removeInitListener(oldListener: ValueInitedListener[T]): Unit = {
    initListener -= oldListener
  }


  def addListener(newListener: ValueChangedListener[T]): Unit = {
    changeListener += newListener
  }

  def removeListener(oldListener: ValueChangedListener[T]): Unit = {
    changeListener -= oldListener
  }


}

object ObservableVar {
  type ValueChangedListener[T] = (T, T) => Any
  type ValueInitedListener[T] = T => Any


}
