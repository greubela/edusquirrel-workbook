package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingReasonExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise.SortingBoard
import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MINOR

case class HtmlSortingReasonExerciseFullscreenElement(
  fullInfo: FullInfo,
  interaction: SortingReasonInteraction
) extends HtmlAppElement {

  private val itemCount = interaction.items.size
  private val fieldCount = interaction.fields.size

  private val stateVar = fullInfo.interactions.boundVar(interaction, MINOR)
  private val reasonDraftVar: Var[String] = Var("")

  private def contentIdSignal(contentId: LanguageMapContentId): Signal[String] =
    fullInfo.signals.stringFromLanguageMapId(contentId)

  /** The active item while its reason is not yet confirmed. */
  private val reasonFormTargetIndex: Signal[Option[Int]] = stateVar.signal
    .map { rawState =>
      val state = rawState.sanitized(itemCount, fieldCount)
      state.activeItemIndex(itemCount, fieldCount).filterNot(state.isReasonConfirmed(_, itemCount, fieldCount))
    }
    .distinct

  /** Starts the reason draft from the saved reason whenever a new item needs one; bound to the mounted board. */
  private def seedReasonDraft(target: Option[Int]): Unit =
    target.foreach(itemIndex => reasonDraftVar.set(stateVar.now().sanitized(itemCount, fieldCount).reasonsByItem(itemIndex)))

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

  private val board = SortingBoard(
    fullInfo, stateVar,
    interaction.fields,
    interaction.items.map(item => SortingBoard.Item(item.label, item.correctFieldIndex, item.wrongFeedback)),
    rootClass = "sorting-exercise-fullscreen sorting-reason-exercise-fullscreen",
    canDragActive = (state, index) => state.isReasonConfirmed(index, itemCount, fieldCount),
    markWaitingItems = true,
    sourceExtras = Seq(reasonFormTargetIndex --> seedReasonDraft, child.maybe <-- reasonFormTargetIndex.map(_.map(renderReasonForm))),
    onReset = () => reasonDraftVar.set(""))

  override def getDomElement(): Element = board.element
}
