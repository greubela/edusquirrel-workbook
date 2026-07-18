package it.evadid.evacuation.eva1.algorithm.events.traits

trait EventManager[T <: Event] {

  def addListener(listener: T => Any): Unit
  def removeListener(listener: T => Any): Unit

}
