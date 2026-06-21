package it.evadid.evacuation.core.datastructures.maps

trait MultiHashMap[K, V, T[Z <: V]] {
/*

  def contains(k: K): Boolean

  def keys(): Iterable[K]

  def clear(): Unit

  def addAll(otherMap: MultiHashMap[K, V, _]): Unit

  def getAllEntries: Set[(K, V)]

  def getAllValues: Set[V]

  def getCopy: MultiHashMap[K, V, T[T]]

  def getCopyWithMappedKeys[KK](function: K => KK): MultiHashMap[KK, V, T]

  def getCopyWithFilteredKeys(function: K => Boolean): MultiHashMap[K, V, T]

  def getCopyWithReplacedValues[O](function: Seq[V] => Seq[O]): MultiHashMap[K, O, T]

  def getCopyWithApplied[O](function: V => O): MultiHashMap[K, O, C]

  def +=(kv: (K, C)): MultiHashMap[K, V, C]

  def apply(k: K): C

  def getOrElse(k: K, v: C): C

  def -=(key: K): MultiHashMap[K, V, C]

  def get(key: K): Option[C]

  def addElement(kv: (K, V)): MultiHashMap[K, V, C]

  def removeAllValues(useFilter: V => Boolean): MultiHashMap[K, V, C]

  def -=(kv: (K, V)): MultiHashMap[K, V, C]


  def removeElement(kv: (K, V)): MultiHashMap[K, V, C]


  def print(): Unit = {
    keys().foreach(key => {
      println("key \"" + key + "\":")
      this (key).foreach(value => {
        println("    " + value)
      })
    })
  }

  override def toString: String = {
    var str: String = ""
    keys().foreach(key => {
      str += "key \"" + key + "\" (" + get(key).get.size + " entries):\n"
      get(key).get.foreach(value => {
        str += "\t" + value + "\n"
      })
    })
    str + "\n"
  }
*/

}
