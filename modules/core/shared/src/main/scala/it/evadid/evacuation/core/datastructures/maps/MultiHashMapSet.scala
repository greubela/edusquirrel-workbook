package it.evadid.evacuation.core.datastructures.maps

import scala.collection.mutable

class MultiHashMapSet[K, V] {

  private val map: mutable.Map[K, mutable.HashSet[V]] = mutable.HashMap[K, mutable.HashSet[V]]()

  def contains(k: K): Boolean = map.contains(k)

  def keys(): Iterable[K] = map.keys

  def clear(): Unit = map.clear()

  private def snapshot: List[(K, Set[V])] = this.synchronized {
    map.iterator.map((key, values) => key -> values.toSet).toList
  }

  def addAll(otherMap: MultiHashMapSet[K, V]): Unit =
    otherMap.snapshot.foreach((key, values) => values.foreach(value => addElement(key -> value)))

  def getCopyWithReplacedValues[O](function: Seq[V] => Seq[O]): MultiHashMapSet[K, O] = {
    val result = new MultiHashMapSet[K, O]
    snapshot.foreach((key, values) => function(values.toSeq).foreach(value => result.addElement(key -> value)))
    result
  }

  def getCopyWithApplied[O](function: V => O): MultiHashMapSet[K, O] =
    getCopyWithReplacedValues(_.map(function))

  def +=(kv: (K, mutable.HashSet[V])): MultiHashMapSet.this.type = this.synchronized {
    map += kv
    this
  }

  def apply(k: K): mutable.HashSet[V] = this.synchronized { bucket(k) }

  def getOrElse(k: K, v: mutable.HashSet[V]): mutable.HashSet[V] = {
    get(k).getOrElse(v)
  }

  def -=(key: K): MultiHashMapSet.this.type = this.synchronized {
    map -= key
    this
  }

  def iterator: Iterator[(K, mutable.HashSet[V])] = this.synchronized {
    map.iterator
  }

  def get(key: K): Option[mutable.HashSet[V]] = this.synchronized {
    map.get(key)
  }

  def addElement(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    bucket(kv._1) += kv._2
    this
  }

  def removeAllValues(useFilter: V => Boolean): MultiHashMapSet.this.type = this synchronized {
    map.valuesIterator.foreach(_.filterInPlace(value => !useFilter(value)))
    this
  }

  def -=(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    map.get(kv._1).foreach(_ -= kv._2)
    this
  }

  def removeElement(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    this.-=(kv)
  }

  private def bucket(key: K): mutable.HashSet[V] = map.getOrElseUpdate(key, mutable.HashSet.empty[V])

  def print(): Unit = {
    keys().foreach(key => {
      println("key \"" + key + "\":")
      this(key).foreach(value => {
        println("    " + value)
      })
    })
  }
  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: MultiHashMapSet[?, ?] => map == other.map
    case _ => false
  }


}
