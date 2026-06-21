package it.evadid.evacuation.eva1.control.traits

import it.evadid.evacuation.core.datastructures.graphs.{Position, Positionable}


case class GraphObjectSelectorState[E <: Positionable](curMousePos: Option[Position], curHighlightDest: Seq[E], curSelected: Seq[E])
