package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.{ObservableValue, Observer}


case class Subscription[T](private val underlying: ObservableValue[T], observer: Observer[T]) {

  def cancel(): Unit = {
    underlying.removeObserver(observer)
  }

}