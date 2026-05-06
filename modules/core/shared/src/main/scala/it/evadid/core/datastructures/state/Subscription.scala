package it.evadid.core.datastructures.state


case class Subscription[T](private val underlying: ObservableValue[T], observer: Observer[T]) {

  def cancel(): Unit = {
    underlying.removeObserver(observer)
  }

}