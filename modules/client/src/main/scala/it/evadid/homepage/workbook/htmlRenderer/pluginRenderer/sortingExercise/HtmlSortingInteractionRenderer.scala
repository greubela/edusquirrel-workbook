package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.sortingExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.plugins.sortingExercise.SortingInteraction

object HtmlSortingInteractionRenderer extends HtmlRenderFactory[SortingInteraction] {

  private val errorCountLabelId = LanguageMapContentId("basic/sortingLastErrorCount")

  override protected def createDomElement(interaction: SortingInteraction): Element = {
    val stateVar = Var(interaction.interactionVariable.currentValue)
    interaction.interactionVariable.observableValue.addObserver(stateVar.set)

    val errorCountSignal =
      stateVar.signal
        .combineWith(fullInfo.signals.stringFromLanguageMapId(errorCountLabelId))
        .map { case (state, template) =>
          val totalErrors = state.lastSessionErrorCount + state.sessionErrorCount
          template.replace("{count}", totalErrors.toString)
        }

    div(
      cls := "workbook-interaction sorting-interaction sorting-interaction--inline",
      div(
        cls := "sorting-interaction__controls",
        HtmlButtonElement.withTextLabel(interaction.openButtonLabel, _ =>
          fullInfo.technical.makeFullscreen(
            HtmlSortingExerciseFullscreenElement(interaction, fullInfo)
          )
        ).getDomElement(),
        span(
          cls := "sorting-interaction__error-count",
          child.text <-- errorCountSignal
        )
      )
    )
  }
}
