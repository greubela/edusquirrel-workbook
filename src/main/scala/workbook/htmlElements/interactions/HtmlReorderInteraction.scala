package workbook.htmlElements.interactions

import com.raquo.laminar.api.L.*
import util.serializing.Serializer
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.AllWorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance

import scala.util.Try

case class HtmlReorderInteraction[T](
                                      workbookInfo: AllWorkbookInfo,
                                      id: String,
                                      elements: List[T],
                                      elementRenderer: T => Element
                                    ) extends WorkbookInteraction[List[Int]] {

  private val defaultOrder: List[Int] = elements.indices.toList

  private val orderSerializer = new Serializer[List[Int]] {
    override def serialize(obj: List[Int]): String = obj.mkString(",")

    override def deserialize(serialized: String): List[Int] = {
      val parsed = serialized
        .split(",")
        .toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap(token => Try(token.toInt).toOption)

      if (parsed.length == elements.length && parsed.toSet == defaultOrder.toSet) parsed
      else defaultOrder
    }
  }

  override val interactionVariable: InteractionVariable[List[Int]] =
    InteractionVariable(this, defaultOrder, orderSerializer)

  private val orderVar = interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MAJOR)

  private val draggingId: Var[Option[Int]] = Var(None)
  private val hoverIndex: Var[Option[Int]] = Var(None)

  private def sanitizeOrder(order: List[Int]): List[Int] =
    if (order.length == elements.length && order.toSet == defaultOrder.toSet) order else defaultOrder

  private def moveItem(current: List[Int], draggedId: Int, insertIndex: Int): List[Int] = {
    val clean = current.filterNot(_ == draggedId)
    val safeIndex = insertIndex.max(0).min(clean.length)
    val (front, back) = clean.splitAt(safeIndex)
    front ++ List(draggedId) ++ back
  }

  private def renderItem(itemId: Int): Element = {
    val content: Element = elementRenderer(elements(itemId))

    div(
      cls := "reorder-item",
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
        val items = container.querySelectorAll(".reorder-item")
        val mouseY = e.clientY

        var newIndex = items.length
        var found = false
        var i = 0
        while (i < items.length && !found) {
          val rect = items.item(i).asInstanceOf[org.scalajs.dom.html.Div].getBoundingClientRect()
          val middleY = rect.top + (rect.height / 2)
          if (mouseY < middleY) { newIndex = i; found = true }
          i += 1
        }

        hoverIndex.set(Some(newIndex))
      },
      onDrop.preventDefault --> { _ =>
        (draggingId.now(), hoverIndex.now()) match {
          case (Some(idToMove), Some(targetIdx)) =>
            val updated = moveItem(sanitizeOrder(orderVar.now()), idToMove, targetIdx)
            orderVar.set(updated)
          case _ =>
        }
        draggingId.set(None)
        hoverIndex.set(None)
      },
      children <-- orderVar.signal.combineWith(draggingId.signal, hoverIndex.signal).map {
        case (rawOrder, dragging, hover) =>
          val ordered = sanitizeOrder(rawOrder)
          val visible = ordered.filterNot(id => dragging.contains(id)).map(renderItem)

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

  override def getDomElement(): Element =
    div(
      cls := "workbook-interaction reorder-interaction",
      listElement
    )
}
