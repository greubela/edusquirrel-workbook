package it.evadid.evacuation.core.datastructures.maps

trait MultiHashMap[K, V, L[X] <: Iterable[X]] extends Map[K, L[V]]

object MultiHashMap {

  def emptyListBased[K, V](): MultiHashMap[K, V, List] = MultiHashMapEntryBased( List(), [A] => (xs: Iterable[A]) => xs.toList)
  def emptySetBased[K, V](): MultiHashMap[K, V, Set] = MultiHashMapEntryBased( Set(), [A] => (xs: Iterable[A]) => xs.toSet)

}

private case class MultiHashMapEntryBased[K, V, L[X] <: Iterable[X]](
                                                                    entries: L[(K, V)],
                                                                    collectionFactory: [A] => Iterable[A] => L[A]
                                                                  ) extends MultiHashMap[K, V, L] {

  private def buildMapFrom(iterable: Iterable[(K, V)]): MultiHashMapEntryBased[K, V, L] = {
    MultiHashMapEntryBased(collectionFactory[(K, V)](iterable), collectionFactory)
  }

  private def buildMapOptionFrom(iterable: Iterable[(K, V)]): Option[MultiHashMapEntryBased[K, V, L]] = {
    if (iterable.isEmpty) None
    else Some(buildMapFrom(iterable))
  }

  private def buildValueFrom(iterable: Iterable[(K, V)]): L[V] = {
    collectionFactory[V](iterable.map(_._2))
  }

  private def buildValueOptionFrom(iterable: Iterable[(K, V)]): Option[L[V]] = {
    if (iterable.isEmpty) None
    else Some(buildValueFrom(iterable))
  }

  override def get(key: K): Option[L[V]] = {
    buildValueOptionFrom(entries.filter(_._1 == key))
  }

  override def iterator: Iterator[(K, L[V])] = {
    entries.groupBy(_._1).iterator.map { case (k, pairs) => k -> buildValueFrom(pairs) }
  }

  override def removed(key: K): MultiHashMapEntryBased[K, V, L] = {
    buildMapFrom(entries.filter(_._1 != key))
  }

  override def updated[V1 >: L[V]](key: K, value: V1): Map[K, V1] = {
    Map.from(iterator).updated(key, value)
  }

  def add(key: K, value: V): MultiHashMapEntryBased[K, V, L] = {
    buildMapFrom(entries.iterator.toList ++ List((key, value)))
  }

  def addAll(key: K, values: Iterable[V]): MultiHashMapEntryBased[K, V, L] = {
    buildMapFrom(entries.iterator.toList ++ values.map(curVal => (key, curVal)))
  }

}