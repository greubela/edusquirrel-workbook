package it.evadid.evacuation.core.datastructures.maps

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object MultiHashMapList {
  given [K: upickle.default.ReadWriter, V: upickle.default.ReadWriter]: upickle.default.ReadWriter[MultiHashMapList[K, V]] =
    upickle.default.readwriter[Map[K, List[V]]].bimap(
      value => value.iterator.map((key, entries) => key -> entries.toList).toMap,
      entries => {
        val result = new MultiHashMapList[K, V]
        entries.foreach { (key, values) => result(key) ++= values }
        result
      })
}

class MultiHashMapList[K, V] {

  private var map: mutable.Map[K, mutable.ListBuffer[V]] = mutable.HashMap[K, mutable.ListBuffer[V]]()

  def contains(k: K): Boolean = map.contains(k)

  def keys(): Iterable[K] = map.keys

  def clear(): Unit = map.clear()

  def addAll(otherMap: MultiHashMapList[K, V]): Unit = {
    val entries = otherMap.iterator.map { case (key, values) => key -> values.toList }.toList
    entries.foreach { case (key, values) => values.foreach(value => addElement(key -> value)) }
  }

  def getAllEntries: Set[(K, V)] = {
    val res: mutable.HashSet[(K, V)] = new mutable.HashSet[(K, V)]()
    keys().foreach(key => {
      this (key).toSeq.foreach(value => {
        res += ((key, value))
      })
    })
    res.toSet
  }

  def getAllValues: Set[V] = {
    val res: mutable.HashSet[V] = new mutable.HashSet[V]()
    keys().foreach(key => {
      this (key).toSeq.foreach(value => {
        res += value
      })
    })
    res.toSet
  }

  def getCopy: MultiHashMapList[K, V] = {
    val res = new MultiHashMapList[K, V]()
    keys().foreach(key => {
      this (key).toSeq.foreach(resValue => {
        res.addElement((key, resValue))
      })
    })
    res
  }

  def getCopyWithMappedKeys[KK](function: K => KK):MultiHashMapList[KK, V] = {
    val res = new MultiHashMapList[KK, V]()
    keys().foreach(key => {
        val transformedKey = function(key)
        this.apply(key).foreach(value => res.addElement((transformedKey, value)))

    })
    res
  }

  def getCopyWithFilteredKeys(function: K => Boolean): MultiHashMapList[K, V] = {
    val res = new MultiHashMapList[K, V]()
    keys().foreach(key => {
      if (function(key)) {
        this.apply(key).foreach(value => res.addElement((key, value)))
      }
    })
    res
  }

  def getCopyWithReplacedValues[O](function: Seq[V] => Seq[O]): MultiHashMapList[K, O] = {
    val res = new MultiHashMapList[K, O]()
    keys().foreach(key => {
      function(this (key).toSeq).foreach(resValue => {
        res.addElement((key, resValue))
      })
    })
    res
  }

  def getCopyWithApplied[O](function: V => O): MultiHashMapList[K, O] = {
    val res = new MultiHashMapList[K, O]()
    keys().foreach(key => {
      this (key).foreach(value => {
        res.addElement((key, function(value)))
      })
    })
    res
  }

  def +=(kv: (K, ListBuffer[V])): MultiHashMapList.this.type = this.synchronized {
    map += kv
    this
  }

  def apply(k: K): ListBuffer[V] = {
    ensureKey(k)
    map(k)
  }

  def getOrElse(k: K, v: ListBuffer[V]): ListBuffer[V] = {
    get(k).getOrElse(v)
  }

  def -=(key: K): MultiHashMapList.this.type = this.synchronized {
    map -= key
    this
  }

  def iterator: Iterator[(K, ListBuffer[V])] = this.synchronized {
    map.iterator
  }

  def get(key: K): Option[ListBuffer[V]] = this.synchronized {
    map.get(key)
  }

  def addElement(kv: (K, V)): MultiHashMapList.this.type = this.synchronized {
    ensureKey(kv._1).get(kv._1).get += kv._2
    this
  }

  def removeAllValues(useFilter: V => Boolean): MultiHashMapList.this.type = this synchronized {
    map.keys.foreach(key => {
      map(key).filter(useFilter).toList.foreach(this.removeElement(key, _))
    })
    this
  }

  def -=(kv: (K, V)): MultiHashMapList.this.type = this.synchronized {
    map.get(kv._1).foreach(_ -= kv._2)
    this
  }

  def removeElement(kv: (K, V)): MultiHashMapList.this.type = this.synchronized {
    this.-=(kv)
  }

  private def ensureKey(k: K): MultiHashMapList.this.type = this.synchronized {
    map.getOrElseUpdate(k, mutable.ListBuffer[V]())
    this
  }



  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: MultiHashMapList[?, ?] => map == other.map
    case _ => false
  }

  def print(): Unit = {
    println(toString())
  }

  override def toString(): String = {
    var str = "MultiHashMap (" + hashCode() + "):\n"
    keys().foreach(key => {
      str += ("    key \"" + key + "\":\n")
      this (key).foreach(value => {
        str += ("        " + value + "\n")
      })
    })
    str

  }


}
