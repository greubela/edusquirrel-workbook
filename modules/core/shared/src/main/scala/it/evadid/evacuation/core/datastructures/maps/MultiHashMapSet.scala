package it.evadid.evacuation.core.datastructures.maps

import scala.collection.mutable

class MultiHashMapSet[K, V] {

  private var map: mutable.Map[K, mutable.HashSet[V]] = mutable.HashMap[K, mutable.HashSet[V]]()

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

  def getCopyWithReplacedValues[O](function: Seq[V] => Seq[O]): MultiHashMapSet[K, O] = {
    val res = new MultiHashMapSet[K, O]()
    keys().foreach(key => {
      function(this(key).toSeq).foreach(resValue => {
        res.addElement( (key, resValue) )
      })
    })
    res
  }

  def getCopyWithApplied[O](function: V => O): MultiHashMapSet[K, O] = {
    val res = new MultiHashMapSet[K, O]()
    keys().foreach(key => {
      this(key).foreach(value => {
        res.addElement( (key, function(value)))
      })
    })
    res
  }

  def +=(kv: (K, mutable.HashSet[V])): MultiHashMapSet.this.type = this.synchronized {
    map += kv
    this
  }

  def apply(k: K): mutable.HashSet[V] = {
    get(k).get
  }

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
    ensureKey(key)
    map.get(key)
  }

  def addElement(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    ensureKey(kv._1).get(kv._1).get += kv._2
    this
  }

  def removeAllValues(useFilter: V => Boolean): MultiHashMapSet.this.type = this synchronized {
    map.keys.foreach(key => {
      map(key).filter(useFilter).toList.foreach(this.removeElement(key, _))
    })
    this
  }

  def -=(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    ensureKey(kv._1).get(kv._1).get -= kv._2
    this
  }

  def removeElement(kv: (K, V)): MultiHashMapSet.this.type = this.synchronized {
    this.-=(kv)
  }

  private def ensureKey(k: K): MultiHashMapSet.this.type = this.synchronized {
    map.getOrElseUpdate(k, mutable.HashSet[V]())
    this
  }

  def print(): Unit = {
    keys().foreach(key => {
      println("key \"" + key + "\":")
      this(key).foreach(value => {
        println("    " + value)
      })
    })
  }
  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = ???


}
