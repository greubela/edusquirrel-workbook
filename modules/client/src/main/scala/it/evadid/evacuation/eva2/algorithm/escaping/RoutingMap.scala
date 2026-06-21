package it.evadid.evacuation.eva2.algorithm.escaping

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.datastructures.matrix
import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix

import scala.collection.MapView

object RoutingMap {

  implicit class RoutingMap(rawRoutingMap: MultiHashMapList[PositionInMatrix, RoutingOption[PositionInMatrix]]) {

    def onlyShortestPaths(): RoutingMap = onlyPathsWithRelativelength(1.0)

    def onlyPathsWithRelativelength(maxFactorInclusive: Double): RoutingMap = {
      rawRoutingMap.getCopyWithReplacedValues(routingOptions => {
        val minDistance = routingOptions.minBy(_.remainingDistance).remainingDistance
        val maxAllowedDistance = maxFactorInclusive * minDistance
        val result: Seq[RoutingOption[PositionInMatrix]] = routingOptions.toList.filter(op => op.remainingDistance <= maxAllowedDistance)
        result
      })
    }

    def getRawMap: MultiHashMapList[PositionInMatrix, RoutingOption[PositionInMatrix]] = rawRoutingMap


    def removeMultipleWaysToSingleGoal(): RoutingMap = {
      rawRoutingMap.getCopyWithReplacedValues(routingOptions => {
        val minDistToGoalMap: MapView[matrix.PositionInMatrix, RoutingOption[PositionInMatrix]] = routingOptions.groupBy(_.destination).view.mapValues(ops => ops.minBy(_.remainingDistance))
        val res = routingOptions.filter(option => option.remainingDistance == minDistToGoalMap(option.destination).remainingDistance)
        res
      })
    }

  }


}
