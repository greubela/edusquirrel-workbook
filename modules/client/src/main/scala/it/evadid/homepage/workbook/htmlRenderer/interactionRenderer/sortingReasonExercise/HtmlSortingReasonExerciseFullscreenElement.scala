package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingReasonExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise.{
  SortingExerciseDragDropHelper,
  SortingFieldLayoutHelper
}
import it.evadid.workbook.elements.interactionElements.sortingExercise.AssignmentResult
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.*
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom.DragEvent
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

case class HtmlSortingReasonExerciseFullscreenElement(
  interaction: SortingReasonInteraction,
  fullInfo: FullInfo
) extends HtmlAppElement with FullscreenLifecycle {

  private val itemCount = interaction.items.size
  private val fieldCount = interaction.fields.size
  private val gridColumns = SortingFieldLayoutHelper.resolvedColumns(fieldCount)

  private val stateVar = interaction.interactionVariable.createBoundVarWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MINOR)
  private val draggingItemIndex: Var[Option[Int]] = Var(None)
  private val errorPopupContentId: Var[Option[LanguageMapContentId]] = Var(None)
  private var wrongFlashTimeout: Option[SetTimeoutHandle] = None
  private val reasonDraftVar: Var[String] = Var("")
  private val reasonFormItemIndex: Var[Option[Int]] = Var(None)

  private val reasonFormTargetIndex: Signal[Option[Int]] = stateVar.signal
    .map { rawState =>
      val state = rawState.sanitized(itemCount, fieldCount)
      state.activeItemIndex(itemCount, fieldCount).filterNot { idx =>
        state.isReasonConfirmed(idx, itemCount, fieldCount)
      }
    }
    .distinct

  reasonFormTargetIndex.foreach {
    case Some(itemIndex) =>
      reasonFormItemIndex.set(Some(itemIndex))
      reasonDraftVar.set(stateVar.now().sanitized(itemCount, fieldCount).reasonsByItem(itemIndex))
    case None =>
      reasonFormItemIndex.set(None)
  }(using com.raquo.laminar.api.L.unsafeWindowOwner)

  override def onFullscreenClose(): Unit = {
    clearWrongFlashTimeout()
    stateVar.update(_.finalizeAttempt().clearWrongPlacementPreview())
  }

  private def finalizeAndReset(): Unit = {
    stateVar.update(state => state.finalizeAttempt().resetAttempt(itemCount))
    errorPopupContentId.set(None)
    clearWrongFlashTimeout()
    SortingExerciseDragDropHelper.clearDragState(draggingItemIndex)
    reasonDraftVar.set("")
    reasonFormItemIndex.set(None)
  }

  private def contentIdSignal(contentId: LanguageMapContentId): Signal[String] =
    fullInfo.signals.stringFromLanguageMapId(contentId)

  private def clearWrongFlashTimeout(): Unit = {
    wrongFlashTimeout.foreach(clearTimeout)
    wrongFlashTimeout = None
  }

  private def scheduleWrongPlacementClear(): Unit = {
    clearWrongFlashTimeout()
    wrongFlashTimeout = Some(
      setTimeout(1000) {
        stateVar.update(_.clearWrongPlacementPreview())
        wrongFlashTimeout = None
      }
    )
  }

  private def handleDropOnField(fieldIndex: Int)(ev: DragEvent): Unit = {
    ev.preventDefault()
    ev.stopPropagation()
    SortingExerciseDragDropHelper.resolvedItemIndex(draggingItemIndex, ev).foreach { itemIndex =>
      val item = interaction.items(itemIndex)
      val state = stateVar.now().sanitized(itemCount, fieldCount)
      val (placementState, result) =
        state.tryAssign(itemIndex, fieldIndex, itemCount, fieldCount, item.correctFieldIndex)

      result match {
        case AssignmentResult.Correct =>
          errorPopupContentId.set(None)
          clearWrongFlashTimeout()
          stateVar.set(
            state.copy(
              placedFieldIndexByItem = placementState.placedFieldIndexByItem,
              wrongPlacementPreview = None
            )
          )
        case AssignmentResult.Wrong =>
          errorPopupContentId.set(Some(item.wrongFeedback))
          stateVar.set(state.recordWrongPlacement(itemIndex, fieldIndex))
          scheduleWrongPlacementClear()
      }
    }
    SortingExerciseDragDropHelper.clearDragState(draggingItemIndex)
  }

  private def preventDragDefault(ev: DragEvent): Unit = {
    ev.preventDefault()
    ev.stopPropagation()
  }

  private def renderReasonForm(itemIndex: Int): Element = {
    val item = interaction.items(itemIndex)
    div(
      cls := "sorting-reason-form",
      div(
        cls := "sorting-reason-form__prompt",
        child.text <-- contentIdSignal(item.reasonPrompt)
      ),
      textArea(
        cls := "sorting-reason-form__input",
        controlled(
          value <-- reasonDraftVar.signal,
          onInput.mapToValue --> reasonDraftVar
        )
      ),
      button(
        typ := "button",
        cls := "sorting-reason-form__confirm-button",
        child.text <-- contentIdSignal(LanguageMapContentId("basic/sortingReasonConfirm")),
        onClick --> { _ =>
          stateVar.update { state =>
            state
              .withReasonDraft(itemIndex, reasonDraftVar.now(), itemCount, fieldCount)
              .confirmReason(itemIndex, itemCount, fieldCount)
          }
        }
      )
    )
  }

  private def renderItemChip(itemIndex: Int, isDraggable: Boolean, extraClass: String = ""): Element = {
    val item = interaction.items(itemIndex)
    val baseClass =
      if extraClass.nonEmpty then s"sorting-item $extraClass"
      else "sorting-item"

    div(
      cls := baseClass,
      cls.toggle("sorting-item--active") <-- Val(isDraggable),
      cls.toggle("sorting-item--upcoming") <-- Val(!isDraggable),
      cls.toggle("sorting-item--waiting-reason") <-- Val(!isDraggable && extraClass.isEmpty),
      draggable := isDraggable,
      onDragStart --> SortingExerciseDragDropHelper.onDragStart(draggingItemIndex, itemIndex),
      onDragEnd --> { _ =>
        SortingExerciseDragDropHelper.scheduleDragEndClear(draggingItemIndex)
      },
      span(child.text <-- contentIdSignal(item.label))
    )
  }

  private def fieldItemIndices(state: SortingReasonInteractionState, fieldIndex: Int): List[Int] = {
    val placedItems = state.placedFieldIndexByItem.zipWithIndex.collect {
      case (Some(placedField), itemIndex) if placedField == fieldIndex => itemIndex
    }
    val previewItems = state.wrongPlacementPreview.toList.collect {
      case (itemIndex, previewField) if previewField == fieldIndex => itemIndex
    }
    placedItems ++ previewItems
  }

  private def renderFieldCard(fieldIndex: Int): Element = {
    val fullWidthClass =
      if SortingFieldLayoutHelper.isFullWidthField(fieldIndex, fieldCount) then " sorting-field-card--full-width"
      else ""

    div(
      cls := s"sorting-field-card$fullWidthClass",
      onDragEnter --> preventDragDefault,
      onDragOver --> preventDragDefault,
      onDrop --> handleDropOnField(fieldIndex),
      div(
        cls := "sorting-field-card__title",
        child.text <-- contentIdSignal(interaction.fields(fieldIndex))
      ),
      div(
        cls := "sorting-field-card__drop",
        onDragEnter.preventDefault --> preventDragDefault,
        onDragOver.preventDefault --> preventDragDefault,
        onDrop --> handleDropOnField(fieldIndex),
        children <-- stateVar.signal.map { rawState =>
          val state = rawState.sanitized(itemCount, fieldCount)
          fieldItemIndices(state, fieldIndex).map { itemIndex =>
            val isWrongPreview = state.wrongPlacementPreview.contains((itemIndex, fieldIndex))
            val extraClass = if isWrongPreview then "sorting-item--wrong" else "sorting-item--correct"
            renderItemChip(itemIndex, isDraggable = false, extraClass = extraClass)
          }
        }
      )
    )
  }

  private val domElement: Element =
    div(
      cls := "sorting-exercise-fullscreen sorting-reason-exercise-fullscreen",
      div(
        cls := "sorting-exercise-fullscreen__toolbar",
        span(
          cls := "sorting-exercise-fullscreen__session-errors",
          child.text <-- stateVar.signal
            .combineWith(contentIdSignal(LanguageMapContentId("basic/sortingSessionErrorCount")))
            .map { case (state, template) =>
              template.replace("{count}", state.sessionErrorCount.toString)
            }
        ),
        button(
          typ := "button",
          cls := "sorting-exercise-fullscreen__reset-button",
          child.text <-- contentIdSignal(LanguageMapContentId("basic/sortingResetExercise")),
          onClick --> { _ => finalizeAndReset() }
        )
      ),
      div(
        cls := s"sorting-fields sorting-fields--cols-$gridColumns",
        interaction.fields.indices.map(renderFieldCard)
      ),
      div(
        cls := "sorting-source",
        div(
          cls := "sorting-source__label",
          child.text <-- contentIdSignal(LanguageMapContentId("basic/sortingCurrentTerm"))
        ),
        child.maybe <-- reasonFormTargetIndex.map(_.map(renderReasonForm)),
        div(
          cls := "sorting-source__items",
          children <-- stateVar.signal.map { rawState =>
            val state = rawState.sanitized(itemCount, fieldCount)
            val activeIndex = state.activeItemIndex(itemCount, fieldCount)
            val previewItemIndex = state.wrongPlacementPreview.map(_._1)

            val activeElements = activeIndex.toList
              .filterNot(previewItemIndex.contains)
              .map { index =>
                val isDraggable = state.isReasonConfirmed(index, itemCount, fieldCount)
                renderItemChip(index, isDraggable = isDraggable)
              }
            val upcomingElements = state.placedFieldIndexByItem.zipWithIndex.collect {
              case (None, index) if !activeIndex.contains(index) && !previewItemIndex.contains(index) =>
                renderItemChip(index, isDraggable = false, extraClass = "sorting-item--upcoming")
            }
            activeElements ++ upcomingElements
          }
        )
      ),
      div(
        cls := "sorting-success-banner",
        cls.toggle("is-visible") <-- stateVar.signal.map(_.sanitized(itemCount, fieldCount).isComplete(itemCount, fieldCount)),
        child.text <-- contentIdSignal(LanguageMapContentId("basic/sortingFeedbackSuccess"))
      ),
      div(
        cls := "sorting-error-popup",
        cls.toggle("is-visible") <-- errorPopupContentId.signal.map(_.nonEmpty),
        div(
          cls := "sorting-error-popup__content",
          child.text <-- errorPopupContentId.signal.flatMapSwitch {
            case Some(contentId) => contentIdSignal(contentId)
            case None => Val("")
          },
          button(
            typ := "button",
            cls := "sorting-error-popup__button",
            child.text <-- contentIdSignal(LanguageMapContentId("basic/sortingErrorPopupOk")),
            onClick --> { _ => errorPopupContentId.set(None) }
          )
        )
      )
    )

  override def getDomElement(): Element = domElement
}
