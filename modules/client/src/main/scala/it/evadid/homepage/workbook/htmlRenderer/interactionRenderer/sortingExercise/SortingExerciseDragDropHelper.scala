package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import com.raquo.laminar.api.L.Var
import org.scalajs.dom.DragEvent
import scala.scalajs.js.timers.setTimeout

object SortingExerciseDragDropHelper {

  private val dataTransferType = "application/x-sorting-item-index"
  private var lastDraggedItemIndex: Option[Int] = None

  def onDragStart(draggingItemIndex: Var[Option[Int]], itemIndex: Int)(ev: DragEvent): Unit = {
    lastDraggedItemIndex = Some(itemIndex)
    draggingItemIndex.set(Some(itemIndex))
    val asText = itemIndex.toString
    ev.dataTransfer.setData(dataTransferType, asText)
    ev.dataTransfer.setData("text/plain", asText)
  }

  def scheduleDragEndClear(draggingItemIndex: Var[Option[Int]]): Unit =
    setTimeout(0) {
      draggingItemIndex.set(None)
    }

  def clearDragState(draggingItemIndex: Var[Option[Int]]): Unit = {
    draggingItemIndex.set(None)
    lastDraggedItemIndex = None
  }

  def resolvedItemIndex(draggingItemIndex: Var[Option[Int]], ev: DragEvent): Option[Int] = {
    def parseTransferred(raw: String): Option[Int] =
      if raw.isEmpty then None else raw.toIntOption

    draggingItemIndex.now()
      .orElse(lastDraggedItemIndex)
      .orElse(parseTransferred(ev.dataTransfer.getData(dataTransferType)))
      .orElse(parseTransferred(ev.dataTransfer.getData("text/plain")))
  }
}
