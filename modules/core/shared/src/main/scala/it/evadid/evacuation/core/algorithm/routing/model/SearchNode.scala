package it.evadid.evacuation.core.algorithm.routing.model

case class SearchNode[N, I](node: N, predecessor: Option[N], info: I)
