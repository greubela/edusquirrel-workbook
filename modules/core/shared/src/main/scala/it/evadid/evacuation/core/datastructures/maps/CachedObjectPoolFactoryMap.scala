package it.evadid.evacuation.core.datastructures.maps

import scala.collection.mutable

class CachedObjectPoolFactoryMap[K, V](factories: Seq[K => Option[V]]) extends Map[K, V] {


  private val map = mutable.Map[K, V]()

  def clear(): Unit = map.synchronized {
    map.clear()
  }

  def +=(kv: (K, V)): CachedObjectPoolFactoryMap.this.type = map.synchronized {
    map += kv
    this
  }

  def -=(key: K): CachedObjectPoolFactoryMap.this.type = map.synchronized {
    map -= key
    this
  }

  override def get(key: K): Option[V] = map.synchronized {
    val getDirect = map.get(key)
    if (getDirect.isEmpty) {
      val value = getFromFactories(key, factories)
      map += ((key, value.get))
      value
    }
    else getDirect
  }

  @scala.annotation.tailrec
  private def getFromFactories(key: K, factories: Seq[K => Option[V]]): Option[V] = {
    if (factories.isEmpty) None
    else {
      val valFromFactory = factories.head.apply(key)
      if (valFromFactory.nonEmpty) valFromFactory
      else getFromFactories(key, factories.drop(1))
    }
  }


  override def iterator: Iterator[(K, V)] = map.iterator

  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = map.equals(obj)

  override def removed(key: K): Map[K, V] = ???

  override def updated[V1 >: V](key: K, value: V1): Map[K, V1] = ???
}

object CachedObjectPoolFactoryMap {

  def apply[K, V](factory: K => V): CachedObjectPoolFactoryMap[K, V] = {
    val factories = Seq[K => Option[V]](key => Some(factory.apply(key)))
    new CachedObjectPoolFactoryMap[K, V](factories)
  }

  def apply[K, V](factories: Seq[K => Option[V]]) = new CachedObjectPoolFactoryMap[K, V](factories)

}
