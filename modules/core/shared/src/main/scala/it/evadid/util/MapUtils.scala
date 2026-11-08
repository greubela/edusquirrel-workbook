package it.evadid.util

object MapUtils {

  def mergeMaps[K, V](mapA: Map[K, V], mapB: Map[K, V], mergeValues: (K, V, V) => V): Map[K, V] = {
    val inBoth = mapA.keySet.intersect(mapB.keySet)
    val allKeys = mapA.keySet.union(mapB.keySet)

    allKeys.map(curKey => {
      if (inBoth.contains(curKey)) {
        curKey -> mergeValues(curKey, mapA(curKey), mapB(curKey))
      } else if (mapA.contains(curKey)) {
        curKey -> mapA(curKey)
      } else {
        curKey -> mapB(curKey)
      }
    }).toMap
  }

}
