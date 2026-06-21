package it.evadid.evacuation.core.datastructures.seqs

class MutableObservableSeq[T] extends ObservableSeq[T] {

  def +=(t: T): List[T] = this.synchronized {
    decoratedSeq += t
    addedListener.foreach(_.apply(t))
    toList
  }

  def -=(t: T): List[T] = this.synchronized {
    decoratedSeq -= t
    removedListener.foreach(_.apply(t))
    toList
  }

}