package it.evadid.evacuation.eva1.control.traits.evamouselistener

import it.evadid.evacuation.core.datastructures.graphs.Positionable

trait GraphObjectSelector[O <: Positionable] extends GraphObjectsSelector[O] {

  override def onSelectionFinished(objects: Seq[O]): Unit = {}

  override def getMaxObjectsToSelect: Integer = 1

}
