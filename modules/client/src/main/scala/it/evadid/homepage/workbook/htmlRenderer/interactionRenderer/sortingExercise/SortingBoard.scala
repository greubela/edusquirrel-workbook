package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.workbook.elements.interactionElements.sorting.SortingAttempt
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.AssignmentResult
import org.scalajs.dom.DragEvent

/** The drag-and-drop board shared by the sorting exercises: fields, the source list, the toolbar and the popups,
  * all derived from the attempt in `stateVar`.
  *
  * @param canDragActive    whether the active item may be dragged yet (sorting with reasons waits for a reason)
  * @param sourceExtras     content shown under the source label, e.g. the reason form
  * @param onReset          resets transient content that belongs to the caller
  */
final class SortingBoard[S <: SortingAttempt[S]](
    fullInfo: FullInfo,
    stateVar: Var[S],
    fields: Seq[LanguageMapContentId],
    items: Seq[SortingBoard.Item],
    rootClass: String = "sorting-exercise-fullscreen",
    canDragActive: (S, Int) => Boolean = (_: S, _: Int) => true,
    markWaitingItems: Boolean = false,
    sourceExtras: Seq[Modifier[HtmlElement]] = Seq.empty,
    onReset: () => Unit = () => ()) {

  private val itemCount = items.size
  private val fieldCount = fields.size
  private val gridColumns = SortingFieldLayoutHelper.resolvedColumns(fieldCount)
  private val draggingItemIndex: Var[Option[Int]] = Var(None)
  private val errorPopupContentId: Var[Option[LanguageMapContentId]] = Var(None)

  private def text(id: String): Signal[String] = contentIdSignal(LanguageMapContentId(id))
  private def contentIdSignal(contentId: LanguageMapContentId): Signal[String] = fullInfo.signals.stringFromLanguageMapId(contentId)
  private def current: Signal[S] = stateVar.signal.map(_.sanitized(itemCount, fieldCount))

  /** A wrong drop stays visible for a second; a newer wrong drop restarts that second. */
  private val clearWrongPreview: Binder[HtmlElement] =
    stateVar.signal.map(_.wrongPlacementPreview).distinct.changes.filter(_.nonEmpty)
      .flatMapSwitch(_ => EventStream.delay(1000)) --> (_ => stateVar.update(_.clearWrongPlacementPreview()))

  private def finalizeAndReset(): Unit = {
    stateVar.update(_.finalizeAttempt().resetAttempt(itemCount))
    errorPopupContentId.set(None)
    SortingExerciseDragDropHelper.clearDragState(draggingItemIndex)
    onReset()
  }

  private def handleDropOnField(fieldIndex: Int)(ev: DragEvent): Unit = {
    preventDragDefault(ev)
    SortingExerciseDragDropHelper.resolvedItemIndex(draggingItemIndex, ev).foreach { itemIndex =>
      val item = items(itemIndex)
      val state = stateVar.now().sanitized(itemCount, fieldCount)
      state.tryAssign(itemIndex, fieldIndex, itemCount, fieldCount, item.correctFieldIndex) match {
        case (placed, AssignmentResult.Correct) =>
          errorPopupContentId.set(None)
          stateVar.set(placed.clearWrongPlacementPreview())
        case (_, AssignmentResult.Wrong) =>
          errorPopupContentId.set(Some(item.wrongFeedback))
          stateVar.set(state.recordWrongPlacement(itemIndex, fieldIndex))
      }
    }
    SortingExerciseDragDropHelper.clearDragState(draggingItemIndex)
  }

  private def preventDragDefault(ev: DragEvent): Unit = {
    ev.preventDefault()
    ev.stopPropagation()
  }

  private def renderItemChip(itemIndex: Int, isDraggable: Boolean, extraClass: String = ""): Element = div(
    cls := (if extraClass.nonEmpty then s"sorting-item $extraClass" else "sorting-item"),
    cls("sorting-item--active") := isDraggable,
    cls("sorting-item--upcoming") := !isDraggable,
    cls("sorting-item--waiting-reason") := markWaitingItems && !isDraggable && extraClass.isEmpty,
    draggable := isDraggable,
    onDragStart --> SortingExerciseDragDropHelper.onDragStart(draggingItemIndex, itemIndex),
    onDragEnd --> (_ => SortingExerciseDragDropHelper.scheduleDragEndClear(draggingItemIndex)),
    span(child.text <-- contentIdSignal(items(itemIndex).label))
  )

  /** Items placed in the field, followed by a wrong drop previewed there. */
  private def fieldItemIndices(state: S, fieldIndex: Int): Seq[Int] =
    state.placedFieldIndexByItem.zipWithIndex.collect { case (Some(`fieldIndex`), itemIndex) => itemIndex } ++
      state.wrongPlacementPreview.collect { case (itemIndex, `fieldIndex`) => itemIndex }

  private def renderFieldCard(fieldIndex: Int): Element = div(
    cls := "sorting-field-card",
    cls("sorting-field-card--full-width") := SortingFieldLayoutHelper.isFullWidthField(fieldIndex, fieldCount),
    onDragEnter --> preventDragDefault,
    onDragOver --> preventDragDefault,
    onDrop --> handleDropOnField(fieldIndex),
    div(cls := "sorting-field-card__title", child.text <-- contentIdSignal(fields(fieldIndex))),
    div(
      cls := "sorting-field-card__drop",
      onDragEnter.preventDefault --> preventDragDefault,
      onDragOver.preventDefault --> preventDragDefault,
      onDrop --> handleDropOnField(fieldIndex),
      children <-- current.map { state =>
        fieldItemIndices(state, fieldIndex).map { itemIndex =>
          val wrong = state.wrongPlacementPreview.contains((itemIndex, fieldIndex))
          renderItemChip(itemIndex, isDraggable = false, extraClass = if wrong then "sorting-item--wrong" else "sorting-item--correct")
        }
      }
    )
  )

  /** The active item, then the items still to come; an item previewed in a field is shown there instead. */
  private def renderSourceItems(state: S): Seq[Element] = {
    val active = state.activeItemIndex(itemCount, fieldCount)
    val previewed = state.wrongPlacementPreview.map(_._1)
    active.filterNot(previewed.contains).toList.map(index => renderItemChip(index, isDraggable = canDragActive(state, index))) ++
      state.placedFieldIndexByItem.zipWithIndex.collect {
        case (None, index) if !active.contains(index) && !previewed.contains(index) =>
          renderItemChip(index, isDraggable = false, extraClass = "sorting-item--upcoming")
      }
  }

  val element: Element = div(
    onUnmountCallback(_ => stateVar.update(_.finalizeAttempt().clearWrongPlacementPreview())),
    clearWrongPreview,
    cls := rootClass,
    div(
      cls := "sorting-exercise-fullscreen__toolbar",
      span(
        cls := "sorting-exercise-fullscreen__session-errors",
        child.text <-- stateVar.signal.combineWith(text("basic/sortingSessionErrorCount"))
          .map((state, template) => template.replace("{count}", state.sessionErrorCount.toString))
      ),
      button(
        typ := "button",
        cls := "sorting-exercise-fullscreen__reset-button",
        child.text <-- text("basic/sortingResetExercise"),
        onClick --> (_ => finalizeAndReset())
      )
    ),
    div(cls := s"sorting-fields sorting-fields--cols-$gridColumns", fields.indices.map(renderFieldCard)),
    div(
      cls := "sorting-source",
      div(cls := "sorting-source__label", child.text <-- text("basic/sortingCurrentTerm")),
      sourceExtras,
      div(cls := "sorting-source__items", children <-- current.map(renderSourceItems))
    ),
    div(
      cls := "sorting-success-banner",
      cls("is-visible") <-- current.map(_.isComplete(itemCount, fieldCount)),
      child.text <-- text("basic/sortingFeedbackSuccess")
    ),
    div(
      cls := "sorting-error-popup",
      cls("is-visible") <-- errorPopupContentId.signal.map(_.nonEmpty),
      div(
        cls := "sorting-error-popup__content",
        child.text <-- LabelSelection(errorPopupContentId.signal, items.map(_.wrongFeedback), contentIdSignal)(identity),
        button(
          typ := "button",
          cls := "sorting-error-popup__button",
          child.text <-- text("basic/sortingErrorPopupOk"),
          onClick --> (_ => errorPopupContentId.set(None))
        )
      )
    )
  )
}

object SortingBoard {
  /** What the board needs to know about one item. */
  final case class Item(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId)
}
