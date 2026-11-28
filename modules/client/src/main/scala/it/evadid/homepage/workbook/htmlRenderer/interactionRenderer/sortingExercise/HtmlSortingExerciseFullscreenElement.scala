package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import it.evadid.workbook.control.InteractionSignals.*

import com.raquo.laminar.api.L.*

import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.interactions.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.sync.UpdateImportance.MINOR

case class HtmlSortingExerciseFullscreenElement(fullInfo: FullInfo, interaction: SortingInteraction) extends HtmlAppElement {

  private val board = SortingBoard(
    fullInfo, fullInfo.interactions.boundVar(interaction, MINOR),
    interaction.fields,
    interaction.items.map(item => SortingBoard.Item(item.label, item.correctFieldIndex, item.wrongFeedback)))

  override def getDomElement(): Element = board.element
}
