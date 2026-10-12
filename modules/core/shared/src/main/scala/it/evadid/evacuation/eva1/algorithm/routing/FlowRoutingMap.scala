package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.model.evagraph.Router

object FlowRoutingMap {
  object FlowRoutingMap {
    given upickle.default.ReadWriter[FlowRoutingMap] =
      upickle.default.readwriter[MultiHashMapList[Router, RoutingOption[Router]]].bimap(_.getMap, new FlowRoutingMap(_))
  }

  implicit class FlowRoutingMap(map: MultiHashMapList[Router, RoutingOption[Router]]){

    def getMap: MultiHashMapList[Router, RoutingOption[Router]] = map

  }

}
