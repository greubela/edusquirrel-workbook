package it.evadid.evacuation.eva1.control.traits.evamouselistener

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.Router

abstract class GraphRouterSelector extends GraphObjectSelector[Router] {

  override def getSelectableObjects: Seq[Router] = graph.nodes

  override def onObjectSelected(obj: Router): Unit

  override def onObjectDeselected(obj: Router): Unit

  override def getMaxSelectionDistance(): Option[Int]

  override def graph: EvaGraph

}
