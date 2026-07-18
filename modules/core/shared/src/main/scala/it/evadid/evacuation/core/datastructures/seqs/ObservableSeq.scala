package it.evadid.evacuation.core.datastructures.seqs

import scala.collection.{Iterator, immutable, mutable}

class ObservableSeq[T] extends Seq[T] {

  protected val decoratedSeq: mutable.ListBuffer[T] = mutable.ListBuffer[T]()

  protected val addedListener: mutable.ListBuffer[T => Any] = mutable.ListBuffer[T => Any]()
  protected val removedListener: mutable.ListBuffer[T => Any] = mutable.ListBuffer[T => Any]()

  def addAddedListener(listener: T => Any): Unit = this.synchronized {
    addedListener += listener
  }

  def removeAddedListener(listener: T => Any): Unit = this.synchronized {
    addedListener -= listener
  }

  def addRemovedListener(listener: T => Any): Unit = this.synchronized {
    removedListener += listener
  }

  def removeRemovedListener(listener: T => Any): Unit = this.synchronized {
    removedListener -= listener
  }

  override def toList: immutable.List[T] = this.synchronized {
    decoratedSeq.toList
  }

  override def length: Int = this.synchronized {
    decoratedSeq.length
  }

  override def apply(idx: Int): T = this.synchronized {
    decoratedSeq.apply(idx)
  }

  override def iterator: Iterator[T] = this.synchronized {
    decoratedSeq.iterator
  }

  override def hashCode(): Int = this.synchronized {
    decoratedSeq.hashCode()
  }

  override def equals(that: Any): Boolean = this.synchronized {
    decoratedSeq.equals(that)
  }

}
