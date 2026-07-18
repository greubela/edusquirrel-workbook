package it.evadid.evacuation.core.utility

object DataStructureHelper {


  def reverseMap[K, V](map: Map[K, V]): Map[V, K] = for ((k, v) <- map) yield (v, k)

  def getElementFromSeqSafelyMod[T](seq: Seq[T], nr: Long): T = {
      val safeNumber = if (nr > 0) nr % seq.size else ((nr % seq.size) + seq.size) % seq.size
      seq(safeNumber.toInt)
    }

















}
