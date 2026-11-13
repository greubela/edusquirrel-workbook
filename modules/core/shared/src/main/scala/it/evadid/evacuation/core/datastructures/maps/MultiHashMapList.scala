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

  private val map: mutable.Map[K, mutable.ListBuffer[V]] = mutable.HashMap[K, mutable.ListBuffer[V]]()

  def contains(k: K): Boolean = map.contains(k)

  def keys(): Iterable[K] = map.keys

  def clear(): Unit = map.clear()

  private def snapshot: List[(K, List[V])] = this.synchronized {
    map.iterator.map((key, values) => key -> values.toList).toList
  }

  def addAll(otherMap: MultiHashMapList[K, V]): Unit =
    otherMap.snapshot.foreach((key, values) => values.foreach(value => addElement(key -> value)))

  def getAllEntries: Set[(K, V)] = snapshot.iterator.flatMap((key, values) => values.map(key -> _)).toSet

  def getAllValues: Set[V] = snapshot.iterator.flatMap(_._2).toSet

  private def copyEntries[KK, O](entries: Iterator[(KK, IterableOnce[O])]): MultiHashMapList[KK, O] = {
    val result = new MultiHashMapList[KK, O]
    entries.foreach((key, values) => values.iterator.foreach(value => result.addElement(key -> value)))
    result
  }

  def getCopy: MultiHashMapList[K, V] = copyEntries(snapshot.iterator)

  def getCopyWithMappedKeys[KK](function: K => KK): MultiHashMapList[KK, V] =
    copyEntries(snapshot.iterator.map((key, values) => function(key) -> values))

  def getCopyWithFilteredKeys(function: K => Boolean): MultiHashMapList[K, V] =
    copyEntries(snapshot.iterator.filter((key, _) => function(key)))

  def getCopyWithReplacedValues[O](function: Seq[V] => Seq[O]): MultiHashMapList[K, O] =
    copyEntries(snapshot.iterator.map((key, values) => key -> function(values)))

  def getCopyWithApplied[O](function: V => O): MultiHashMapList[K, O] =
    getCopyWithReplacedValues(_.map(function))

  def +=(kv: (K, ListBuffer[V])): MultiHashMapList.this.type = this.synchronized {
    map += kv
    this
  }

  def apply(k: K): ListBuffer[V] = this.synchronized {
    bucket(k)
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
    bucket(kv._1) += kv._2
    this
  }

  def removeAllValues(useFilter: V => Boolean): MultiHashMapList.this.type = this synchronized {
    map.valuesIterator.foreach(_.filterInPlace(value => !useFilter(value)))
    this
  }

  def -=(kv: (K, V)): MultiHashMapList.this.type = this.synchronized {
    map.get(kv._1).foreach(_ -= kv._2)
    this
  }

  def removeElement(kv: (K, V)): MultiHashMapList.this.type = this.synchronized {
    this.-=(kv)
  }

  private def bucket(key: K): ListBuffer[V] = map.getOrElseUpdate(key, ListBuffer.empty[V])

  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: MultiHashMapList[?, ?] => map == other.map
    case _ => false
  }

  def print(): Unit = {
    println(toString())
  }

  override def toString(): String =
    snapshot.map { (key, values) =>
      s"    key \"$key\":\n" + values.map(value => s"        $value\n").mkString
    }.mkString(s"MultiHashMap (${hashCode()}):\n", "", "")

}
