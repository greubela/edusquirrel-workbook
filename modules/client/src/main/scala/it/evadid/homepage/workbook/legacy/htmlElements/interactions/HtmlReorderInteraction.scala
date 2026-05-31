package it.evadid.homepage.workbook.legacy.htmlElements.interactions

import com.raquo.laminar.api.L.*
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable

import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import scala.util.Try
case class HtmlReorderInteraction[T](
                                      fullInfo: FullInfo,
                                      id: String,
                                      elements: List[T],
                                      elementRenderer: T => Element
                                    ) extends WorkbookInteraction[List[Int]] {
  
  override val serializer: Serializer[List[Int]] = orderSerializer
  
  override val defaultValue: List[Int] = elements.indices.toList // todo: shuffled?

  private val orderSerializer = new Serializer[List[Int]] {
    override def serialize(obj: List[Int]): String = obj.mkString(",")

    override def deserialize(serialized: String): List[Int] = {
      val parsed = serialized
        .split(",")
        .toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap(token => Try(token.toInt).toOption)

      if (parsed.length == elements.length && parsed.toSet == defaultValue.toSet) parsed
      else defaultValue
    }
  }

  override val interactionVariable: InteractionVariable[List[Int]] =
    InteractionVariable(this, orderSerializer)

  private val orderVar = interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MINOR)

  private val draggingId: Var[Option[Int]] = Var(None)
  private val hoverIndex: Var[Option[Int]] = Var(None)

  private def sanitizeOrder(order: List[Int]): List[Int] =
    if (order.length == elements.length && order.toSet == defaultValue.toSet) order else defaultValue

  private def moveItem(current: List[Int], draggedId: Int, insertIndex: Int): List[Int] = {
    val fromIndex = current.indexOf(draggedId)
    if (fromIndex < 0) return current
    val clean = current.filterNot(_ == draggedId)
    val adjustedInsertIndex = if (insertIndex > fromIndex) insertIndex - 1 else insertIndex
    val safeIndex = adjustedInsertIndex.max(0).min(clean.length)
    val (front, back) = clean.splitAt(safeIndex)
    front ++ List(draggedId) ++ back
  }

  private def dropIndex(container: org.scalajs.dom.html.Div, mouseY: Double): Int = {
    val items = container.querySelectorAll(".reorder-item")
    var newIndex = items.length
    var found = false
    var i = 0
    while (i < items.length && !found) {
      val rect = items.item(i).asInstanceOf[org.scalajs.dom.html.Div].getBoundingClientRect()
      val middleY = rect.top + (rect.height / 2)
      if (mouseY < middleY) {
        newIndex = i
        found = true
      }
      i += 1
    }
    newIndex
  }

  private def renderItem(itemId: Int): Element = {
    val content: Element = elementRenderer(elements(itemId))

    div(
      cls := "reorder-item",
      cls.toggle("reorder-item--dragging") <-- draggingId.signal.map(_.contains(itemId)),
      draggable := true,
      onDragStart --> (_ => draggingId.set(Some(itemId))),
      onDragEnd --> (_ => {
        draggingId.set(None)
        hoverIndex.set(None)
      }),
      content
    )
  }

  private val listElement: Element =
    div(
      cls := "reorder-list",
      onDragOver.preventDefault --> { e =>
        val container = e.currentTarget.asInstanceOf[org.scalajs.dom.html.Div]
        hoverIndex.set(Some(dropIndex(container, e.clientY)))
      },
      onDrop.preventDefault --> { e =>
        val container = e.currentTarget.asInstanceOf[org.scalajs.dom.html.Div]
        val targetIdx = dropIndex(container, e.clientY)
        draggingId.now() match {
          case Some(idToMove) =>
            val updated = moveItem(sanitizeOrder(orderVar.now()), idToMove, targetIdx)
            orderVar.set(updated)
          case _ =>
        }
        draggingId.set(None)
        hoverIndex.set(None)
      },
      children <-- orderVar.signal.combineWith(hoverIndex.signal).map {
        case (rawOrder, hover) =>
          val ordered = sanitizeOrder(rawOrder)
          val visible = ordered.map(renderItem)

          hover match {
            case Some(index) =>
              val safe = index.max(0).min(visible.length)
              val (front, back) = visible.splitAt(safe)
              front ++ List(div(cls := "drop-placeholder")) ++ back
            case None =>
              visible
          }
      }
    )

  def getDomElement(): Element =
    div(
      cls := "workbook-interaction reorder-interaction",
      listElement
    )
}
