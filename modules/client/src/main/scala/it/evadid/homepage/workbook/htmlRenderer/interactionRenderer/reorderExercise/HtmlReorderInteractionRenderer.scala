package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.reorderExercise.{ReorderCorrectness, ReorderInteraction, ReorderInteractionState}
import it.evadid.workbook.interaction.sync.UpdateImportance

object HtmlReorderInteractionRenderer extends LineBasedRenderingFactory[ReorderInteraction[?]] {

  override protected def createRendering(reorder: ReorderInteraction[?]): AtomarLineRendering = {
    val dom: Element = reorder match {
      case codeInteraction: ReorderInteraction.ReorderCodeInteraction =>
        renderReorder(
          codeInteraction,
          (state, itemId: Int) => {
            val contentElement: Element = pre(code(state.elements(itemId)))
            val hintOpt = codeInteraction.hints.lift(itemId)
            hintOpt match {
              case Some(hintId) =>
                div(
                  cls := "reorder-item-content",
                  contentElement,
                  span(
                    cls := "reorder-item-hint",
                    text <-- laminarHelper.plaintextStringSignal(hintId)
                  )
                )
              case None =>
                contentElement
            }
          },
          itemCssClass = "reorder-item--code"
        )
      case mapIds: ReorderInteraction.ReorderMapIdInteraction =>
        renderReorder(
          mapIds,
          (state, itemId) => span(text <-- laminarHelper.plaintextStringSignal(state.elements(itemId))),
          itemCssClass = ""
        )
    }

    AtomarLineRendering.basicLine(reorder, dom, "reorder-interaction")
  }

  private def renderReorder[T](
                                reorder: ReorderInteraction[T],
                                renderElementContent: (ReorderInteractionState[T], Int) => Element,
                                itemCssClass: String
                              ): Element = {
    val stateVar = reorder.interactionVariable.createBoundVarWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MINOR)
    val draggingId: Var[Option[Int]] = Var(None)
    val hoverIndex: Var[Option[Int]] = Var(None)

    def sanitizeOrder(order: List[Int], state: ReorderInteractionState[T]): List[Int] = {
      val validIndices = state.elements.indices.toSet
      if (order.length == state.elements.length && order.toSet == validIndices) order
      else state.correctOrder
    }

    def moveItem(current: List[Int], draggedId: Int, insertIndex: Int): List[Int] = {
      val fromIndex = current.indexOf(draggedId)
      if (fromIndex < 0) return current
      val clean = current.filterNot(_ == draggedId)
      val adjustedInsertIndex = if (insertIndex > fromIndex) insertIndex - 1 else insertIndex
      val safeIndex = adjustedInsertIndex.max(0).min(clean.length)
      val (front, back) = clean.splitAt(safeIndex)
      front ++ List(draggedId) ++ back
    }

    def dropIndex(container: org.scalajs.dom.html.Div, mouseY: Double): Int = {
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

    def renderItem(state: ReorderInteractionState[T], itemId: Int): Element = {
      val baseCls = if (itemCssClass.nonEmpty) s"reorder-item $itemCssClass" else "reorder-item"
      div(
        cls := baseCls,
        cls.toggle("reorder-item--dragging") <-- draggingId.signal.map(_.contains(itemId)),
        draggable := true,
        onDragStart --> (_ => draggingId.set(Some(itemId))),
        onDragEnd --> (_ => {
          draggingId.set(None)
          hoverIndex.set(None)
        }),
        renderElementContent(state, itemId)
      )
    }

    def updateOrder(rawOrder: List[Int]): Unit = {
      reorder.interactionVariable.updateStateFromUserInteraction(
        fullInfo.syncControl,
        state => {
          val sanitized = sanitizeOrder(rawOrder, state)
          if (sanitized != state.currentOrder) state.copy(currentOrder = sanitized)
          else state
        },
        UpdateImportance.MINOR
      )
    }

    val feedbackVar: Var[Option[LanguageMapContentId]] = Var(None)

    def checkSolution(): Unit = {
      val state = stateVar.now()

      if (ReorderCorrectness.isOrderCorrect(reorder, state)) {
        feedbackVar.set(Some(LanguageMapContentId("basic/reorderFeedbackSuccess")))
      } else {
        feedbackVar.set(Some(LanguageMapContentId("basic/reorderFeedbackWrongOrder")))
      }
    }

    div(
      cls := "workbook-interaction reorder-interaction",
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
              val state = stateVar.now()
              val updated = moveItem(sanitizeOrder(state.currentOrder, state), idToMove, targetIdx)
              updateOrder(updated)
              feedbackVar.set(None)
            case _ =>
          }
          draggingId.set(None)
          hoverIndex.set(None)
        },
        children <-- stateVar.signal.combineWith(hoverIndex.signal).map {
          case (state, hover) =>
            val ordered = sanitizeOrder(state.currentOrder, state)
            val visible = ordered.map(itemId => renderItem(state, itemId))
            hover match {
              case Some(index) =>
                val safe = index.max(0).min(visible.length)
                val (front, back) = visible.splitAt(safe)
                front ++ List(div(cls := "drop-placeholder")) ++ back
              case None =>
                visible
            }
        }
      ),
      button(
        cls := "btn-check",
        text <-- laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/checkSolution")),
        onClick --> { _ => checkSolution() }
      ),
      div(
        cls := "reorder-feedback",
        child.text <-- feedbackVar.signal.flatMapSwitch {
          case Some(contentId) => laminarHelper.plaintextStringSignal(contentId)
          case None => Val("")
        }
      )
    )
  }
}
