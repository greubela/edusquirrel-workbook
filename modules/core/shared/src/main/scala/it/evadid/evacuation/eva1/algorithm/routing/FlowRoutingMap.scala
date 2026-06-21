package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.model.evagraph.Router

object FlowRoutingMap {

  implicit class FlowRoutingMap(map: MultiHashMapList[Router, RoutingOption[Router]]){

    def getMap: MultiHashMapList[Router, RoutingOption[Router]] = map

  }

}
