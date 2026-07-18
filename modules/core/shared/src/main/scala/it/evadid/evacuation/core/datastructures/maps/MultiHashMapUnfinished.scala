package it.evadid.evacuation.core.datastructures.maps

import scala.collection.mutable

class MultiHashMapUnfinished[K, V, C <: mutable.Growable[V]] {

  private var map: mutable.Map[K, C] = mutable.HashMap[K, C]()

  def contains(k: K): Boolean = map.contains(k)

  def keys(): Iterable[K] = map.keys

  def clear(): Unit = map.clear()

  def addAll(otherMap: MultiHashMapSet[K, V]): Unit = {
    otherMap.keys().foreach(key => {
      otherMap(key).foreach(value => {
        this.addElement( (key, value) )
      })
    })
  }

  def +=(kv: (K, C)): MultiHashMapUnfinished.this.type = this.synchronized {
    map += kv
    this
  }

  def apply(k: K): C = {
    get(k).get
  }

  def getOrElse(k: K, v: C): C = {
    get(k).getOrElse(v)
  }

  def -=(key: K): MultiHashMapUnfinished.this.type = this.synchronized {
    map -= key
    this
  }

  def iterator: Iterator[(K,C)] = this.synchronized {
    map.iterator
  }

  def get(key: K): Option[C] = this.synchronized {
    ensureKey(key)
    map.get(key)
  }

  def addElement(kv: (K, V)): MultiHashMapUnfinished.this.type = this.synchronized {
    var col : C = ensureKey(kv._1).get(kv._1).get
    col += kv._2
    this
  }

  private def ensureKey(k: K): MultiHashMapUnfinished.this.type = ???

  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = ???


}
