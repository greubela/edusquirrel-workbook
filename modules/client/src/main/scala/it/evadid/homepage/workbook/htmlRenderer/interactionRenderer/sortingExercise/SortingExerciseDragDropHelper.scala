package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import com.raquo.laminar.api.L.Var
import org.scalajs.dom.DragEvent
import scala.scalajs.js.timers.setTimeout

object SortingExerciseDragDropHelper {

  private val dataTransferType = "application/x-sorting-item-index"

  def onDragStart(draggingItemIndex: Var[Option[Int]], itemIndex: Int)(ev: DragEvent): Unit = {
    draggingItemIndex.set(Some(itemIndex))
    val asText = itemIndex.toString
    ev.dataTransfer.setData(dataTransferType, asText)
    ev.dataTransfer.setData("text/plain", asText)
  }

  def scheduleDragEndClear(draggingItemIndex: Var[Option[Int]]): Unit =
    setTimeout(0) {
      draggingItemIndex.set(None)
    }

  def clearDragState(draggingItemIndex: Var[Option[Int]]): Unit = draggingItemIndex.set(None)

  def resolvedItemIndex(draggingItemIndex: Var[Option[Int]], ev: DragEvent): Option[Int] = {
    def parseTransferred(raw: String): Option[Int] =
      if raw.isEmpty then None else raw.toIntOption

    // The dragged index travels with the event, so drops also work after the instance cleared its own drag state.
    draggingItemIndex.now()
      .orElse(parseTransferred(ev.dataTransfer.getData(dataTransferType)))
      .orElse(parseTransferred(ev.dataTransfer.getData("text/plain")))
  }
}
