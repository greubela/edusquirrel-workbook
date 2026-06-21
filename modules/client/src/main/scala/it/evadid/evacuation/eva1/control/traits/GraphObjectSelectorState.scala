package it.evadid.evacuation.eva1.control.traits

import it.evadid.core.datastructures.graph.{Positionable}
import it.evadid.evacuation.core.datastructures.graphs.{Position}


case class GraphObjectSelectorState[E <: Positionable](curMousePos: Option[Position], curHighlightDest: Seq[E], curSelected: Seq[E])
