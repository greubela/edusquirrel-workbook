package it.evadid.evacuation.eva1.model.evagraph

import it.evadid.evacuation.eva1.model.evagraph.ConnectionInfo.getConnectionDelayFromSpeed


case class ConnectionInfo(maxParallelism: Int, delayInMs: Int) {

  assert(maxParallelism > 0 && delayInMs >= 0, "connection info out of bounds!")

  def capacityPerSecond: Double = maxParallelism * 1000.0 / delayInMs

  def pxSpeedPerSecond(routerDist: Double): Double = ConnectionInfo.getSpeedInPxPerSecond(routerDist, delayInMs)

  def necessaryBufferTimeInMs: Long = delayInMs / maxParallelism

  def changeParallelism(newPar: Int): ConnectionInfo = if (newPar > 0) ConnectionInfo(newPar, delayInMs) else this

  def changeDelay(newDelayInMs: Int): ConnectionInfo = if (newDelayInMs >= 0) ConnectionInfo(maxParallelism, newDelayInMs) else this

  def changeSpeed(pxDist: Double, newSpeedInPxS: Int): ConnectionInfo = if (newSpeedInPxS > 0) ConnectionInfo(maxParallelism, getConnectionDelayFromSpeed(pxDist, newSpeedInPxS)) else this

}

object ConnectionInfo {

  def apply(maxCapacity: Int, delayInMs: Double): ConnectionInfo = new ConnectionInfo(maxCapacity, Math.round(delayInMs).asInstanceOf[Int])

  def getConnectionDelayFromSpeed(routerDist: Double, speed: Integer = 50): Int = Math.round(routerDist).asInstanceOf[Int] * 1000 / speed

  def getConnectionDelayFromRouterDist(routerDist: Double): Int =
    Math.round(routerDist).asInstanceOf[Int] * 1000 / 50 //GraphicConfigs.pxPerS

  def getSpeedInPxPerSecond(routerDist: Double, delayInMs: Int): Double = routerDist * 1000.0 / delayInMs


}