package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*

import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.interactions.reorderExercise.{ReorderInteraction, ReorderInteractionState}
import it.evadid.workbook.sync.UpdateImportance.MAJOR
import org.scalajs.dom

object HtmlReorderInteractionRenderer extends LineBasedRenderingFactory[ReorderInteraction.ReorderCodeInteraction | ReorderInteraction.ReorderMapIdInteraction] {
  override protected def createRendering(fullInfo: FullInfo, reorder: ReorderInteraction.ReorderCodeInteraction | ReorderInteraction.ReorderMapIdInteraction): AtomarLineRendering = {
    val disabled = reorder.isDisabledState.toAirstreamVar
    // Items that a failed check found misplaced are marked until the answer changes.
    val wrong = fullInfo.reporters.reportersFor(reorder).grading.checks.map(_.wrongParts).distinct
    val dom = reorder match {
      case exercise: ReorderInteraction.ReorderCodeInteraction =>
        renderReorder(fullInfo.interactions.boundVar(exercise, MAJOR), disabled, wrong,
          (state, index) => div(cls := "reorder-item-content", pre(code(state.elements(index))),
            exercise.hints.lift(index).map(id => span(cls := "reorder-item-hint", text <-- laminarHelper(fullInfo).plaintextStringSignal(id)))),
          "reorder-item--code")
      case exercise: ReorderInteraction.ReorderMapIdInteraction =>
        renderReorder(fullInfo.interactions.boundVar(exercise, MAJOR), disabled, wrong,
          (state, index) => span(text <-- laminarHelper(fullInfo).plaintextStringSignal(state.elements(index))), "")
    }
    AtomarLineRendering.basicLine(fullInfo, reorder, dom)
  }

  private def renderReorder[T](state: Var[ReorderInteractionState[T]], disabled: Var[Boolean], wrong: Signal[Set[Int]],
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
        // Items keep their DOM nodes while the order or drop position changes: the browser delivers the drop to the
        // node under the pointer at the last dragover, so re-creating nodes on each dragover would lose the drop.
        children <-- state.signal.combineWith(hover.signal.distinct).map { (answer, target) =>
          val items: Seq[Option[(Int, T)]] = answer.currentOrder.map(index => Some(index -> answer.elements(index)))
          target.fold(items)(index => items.take(index) ++ Seq(None) ++ items.drop(index))
        }.split(identity) { (entry, _, _) =>
          entry.fold(div(cls := "drop-placeholder")) { (index, _) => div(
            cls := s"reorder-item $itemCss",
            cls("reorder-item--dragging") <-- dragging.signal.map(_.contains(index)),
            cls("answer-part--wrong") <-- wrong.map(_.contains(index)),
            draggable <-- disabled.signal.map(!_),
            onDragStart --> { event => if disabled.now() then event.preventDefault() else dragging.set(Some(index)) },
            onDragEnd --> (_ => { dragging.set(None); hover.set(None) }),
            content(state.now(), index))
          }
        }))
  }
}
