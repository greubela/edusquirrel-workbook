package it.evadid.evacuation.eva2.algorithm.escaping

case class EvacuationMetaData(success: Boolean, executionTimeInMs: Long, neighbourhoodFunc: String, strategyName: String) {

}

object EvacuationMetaData {

  def apply(eva: Evacuation, executionTimeInMs: Long, neighbourhoodFunc: String, strategyName: String): EvacuationMetaData = {
    if (eva == null || eva.steps.size <= 1) {
      EvacuationMetaData(false, executionTimeInMs, neighbourhoodFunc, strategyName)
    } else {
      EvacuationMetaData(true, executionTimeInMs, neighbourhoodFunc, strategyName)
    }
  }

}