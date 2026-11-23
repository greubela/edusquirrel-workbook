package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.reorderExercise.{ReorderInteraction, ReorderInteractionState}
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import org.scalajs.dom

object HtmlReorderInteractionRenderer extends LineBasedRenderingFactory[ReorderInteraction.ReorderCodeInteraction | ReorderInteraction.ReorderMapIdInteraction] {
  override protected def createRendering(reorder: ReorderInteraction.ReorderCodeInteraction | ReorderInteraction.ReorderMapIdInteraction): AtomarLineRendering = {
    val disabled = reorder.isDisabledState.toAirstreamVar
    val dom = reorder match {
      case exercise: ReorderInteraction.ReorderCodeInteraction =>
        renderReorder(exercise.interactionVariable.createBoundVarWithUpdateImportance(MAJOR, fullInfo.workbookContextInformation), disabled,
          (state, index) => div(cls := "reorder-item-content", pre(code(state.elements(index))),
            exercise.hints.lift(index).map(id => span(cls := "reorder-item-hint", text <-- laminarHelper.plaintextStringSignal(id)))),
          "reorder-item--code")
      case exercise: ReorderInteraction.ReorderMapIdInteraction =>
        renderReorder(exercise.interactionVariable.createBoundVarWithUpdateImportance(MAJOR, fullInfo.workbookContextInformation), disabled,
          (state, index) => span(text <-- laminarHelper.plaintextStringSignal(state.elements(index))), "")
    }
    AtomarLineRendering.basicLine(reorder, dom)
  }

  private def renderReorder[T](state: Var[ReorderInteractionState[T]], disabled: Var[Boolean],
      content: (ReorderInteractionState[T], Int) => Element, itemCss: String): Element = {
    val dragging = Var(Option.empty[Int])
    val hover = Var(Option.empty[Int])
    def dropIndex(container: dom.html.Div, mouseY: Double): Int = {
      val items = container.querySelectorAll(".reorder-item")
      (0 until items.length).find { index =>
        val rect = items.item(index).asInstanceOf[dom.html.Div].getBoundingClientRect()
        mouseY < rect.top + rect.height / 2
      }.getOrElse(items.length)
    }
    div(cls := "workbook-interaction reorder-interaction",
      div(cls := "reorder-list",
        onDragOver.preventDefault --> { event =>
          if !disabled.now() then hover.set(Some(dropIndex(event.currentTarget.asInstanceOf[dom.html.Div], event.clientY)))
        },
        onDrop.preventDefault --> { event =>
          if !disabled.now() then dragging.now().foreach { index =>
            val target = dropIndex(event.currentTarget.asInstanceOf[dom.html.Div], event.clientY)
            state.update(_.moveElementToIndex(index, target))
          }
          dragging.set(None)
          hover.set(None)
        },
        children <-- state.signal.combineWith(hover.signal).map { (answer, target) =>
          val items = answer.currentOrder.map { index => div(
            cls := s"reorder-item $itemCss",
            cls("reorder-item--dragging") <-- dragging.signal.map(_.contains(index)),
            draggable <-- disabled.signal.map(!_),
            onDragStart --> { event => if disabled.now() then event.preventDefault() else dragging.set(Some(index)) },
            onDragEnd --> (_ => { dragging.set(None); hover.set(None) }),
            content(answer, index))
          }
          target match {
            case Some(index) => items.take(index) ++ Seq(div(cls := "drop-placeholder")) ++ items.drop(index)
            case None => items
          }
        }))
  }
}
