package it.evadid.evacuation.eva1.control.traits.evamouselistener

import it.evadid.evacuation.core.datastructures.graphs.{Position, Positionable}
import it.evadid.evacuation.eva1.control.traits.{GraphObjectSelectorState, VisualizableMouseListener}
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph

abstract class GraphObjectsSelector[O <: Positionable] extends VisualizableMouseListener[O] {

  private var selectedObjects: List[O] = List()

  private var curHighlightDest: Option[O] = None
  private var curMousePos: Option[Position] = None

  def getState(): GraphObjectSelectorState[O] = GraphObjectSelectorState(curMousePos, curHighlightDest.toList, selectedObjects)

  override def onMouseEntered(x: Double, y: Double): Unit = {}

  override def onMouseExited(x: Double, y: Double): Unit = {
    curMousePos = None
  }

  def deselectAll(): Unit = {
    selectedObjects.foreach(onObjectDeselected)
    selectedObjects = List()
  }

  override def onMouseClicked(x: Double, y: Double, primaryButton: Boolean): Unit = {

    val next = calcNearestSelectableObject(x, y)

    if (next.isDefined && !selectedObjects.contains(next) && selectedObjects.size < getMaxObjectsToSelect) {
      selectedObjects = selectedObjects ++ next
      onObjectSelected(next.get)
    }
    else if (next.isDefined && selectedObjects.contains(next)) {
      selectedObjects = selectedObjects.filter(_ != next.get)
      onObjectDeselected(next.get)
    }
    else if (next.isDefined && !selectedObjects.contains(next) && selectedObjects.size >= getMaxObjectsToSelect) {
      val removeFromSelection = selectedObjects.head
      selectedObjects = selectedObjects.tail ++ next
      onObjectDeselected(removeFromSelection)
      onObjectSelected(next.get)
    }

    if (selectedObjects.size == getMaxObjectsToSelect) {
      onSelectionFinished(selectedObjects)
      if (automaticDeselect()) selectedObjects = List()
    }

  }


  override def onMouseMoved(x: Double, y: Double): Unit = {
    curHighlightDest = calcNearestSelectableObject(x, y)
    curMousePos = Some(Position(x, y))
  }

  private def calcNearestSelectableObject(x: Double, y: Double): Option[O] = {
    val next: Option[O] = getSelectableObjects.minByOption(_.distTo(Position(x, y)))
    var res: Option[O] = None
    if (next.isDefined) {
      val dist = next.get.distTo(Position(x, y))
      if (getMaxSelectionDistance().isDefined && getMaxSelectionDistance().get > dist) {
        res = next
      }
    }
    res
  }

  def getSelectableObjects: Seq[O]

  def onObjectSelected(obj: O): Unit

  def onObjectDeselected(obj: O): Unit

  def onSelectionFinished(objects: Seq[O]): Unit

  def graph: EvaGraph

  def getMaxSelectionDistance(): Option[Int]

  def getMaxObjectsToSelect: Integer

  def automaticDeselect(): Boolean

}
