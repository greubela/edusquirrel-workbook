package it.evadid.evacuation.eva1.algorithm.events.traits

trait Event {
  val timestampInMs: Long = System.currentTimeMillis()
}
