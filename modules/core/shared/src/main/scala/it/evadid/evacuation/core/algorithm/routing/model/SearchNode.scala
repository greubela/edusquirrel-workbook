package it.evadid.evacuation.core.algorithm.routing.model

import upickle.default.ReadWriter

case class SearchNode[N, I](node: N, predecessor: Option[N], info: I) derives ReadWriter
