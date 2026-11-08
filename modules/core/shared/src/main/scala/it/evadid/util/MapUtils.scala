package it.evadid.util

import it.evadid.core.util.io.TypeConverter

object MapUtils {



  def convertMap[K1, V1, K2, V2](mapA: Map[K1, V1])(keyConverter: TypeConverter[K1, K2], valueConverter: TypeConverter[V1, V2]): (Map[K2, V2], Set[(K1, V1)]) = {
    val tupConverter: TypeConverter[(K1, V1), (K2, V2)] = TypeConverter.tupleConverter(keyConverter, valueConverter)
    val dest = tupConverter.tryConvertAllToO(mapA.toList)
    (dest.outputAfterOpteration.toMap, dest.inputAfterOperation)
  }

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
