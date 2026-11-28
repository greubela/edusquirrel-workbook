package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import it.evadid.workbook.interaction.state.InteractionSignals.*

import com.raquo.laminar.api.L.*

import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MINOR

case class HtmlSortingExerciseFullscreenElement(fullInfo: FullInfo, interaction: SortingInteraction) extends HtmlAppElement {

  private val board = SortingBoard(
    fullInfo, fullInfo.interactions.boundVar(interaction, MINOR),
    interaction.fields,
    interaction.items.map(item => SortingBoard.Item(item.label, item.correctFieldIndex, item.wrongFeedback)))

  override def getDomElement(): Element = board.element
}
