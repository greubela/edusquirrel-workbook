package it.evadid.evacuation.core.datastructures.seqs

class MutableObservableSeq[T] extends ObservableSeq[T] {

  def +=(t: T): List[T] = this.synchronized {
    decoratedSeq += t
    addedListener.toList.foreach(_.apply(t))
    toList
  }

  def -=(t: T): List[T] = this.synchronized {
    if (decoratedSeq.contains(t)) {
      decoratedSeq -= t
      removedListener.toList.foreach(_.apply(t))
    }
    toList
  }

}