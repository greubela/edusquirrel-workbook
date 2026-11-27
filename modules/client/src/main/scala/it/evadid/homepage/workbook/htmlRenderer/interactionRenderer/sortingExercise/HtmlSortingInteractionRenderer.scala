package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteraction

object HtmlSortingInteractionRenderer extends LineBasedRenderingFactory[SortingInteraction] {

  private val errorCountLabelId = LanguageMapContentId("basic/sortingLastErrorCount")

  override protected def createRendering(fullInfo: FullInfo, interaction: SortingInteraction): AtomarLineRendering = {
    val errorCountSignal =
      interaction.interactionVariable.createInteractionSignal
        .combineWith(fullInfo.signals.stringFromLanguageMapId(errorCountLabelId))
        .map { case (state, template) =>
          val totalErrors = state.lastSessionErrorCount + state.sessionErrorCount
          template.replace("{count}", totalErrors.toString)
        }

    val dom: Element =
      div(
        cls := "sorting-interaction sorting-interaction--inline",
        div(
          cls := "sorting-interaction__controls",
          HtmlButtonElement.withTextLabel(
            fullInfo, interaction.openButtonLabel,
            _ => fullInfo.displayControl.setFullscreen(HtmlSortingExerciseFullscreenElement(fullInfo, interaction)),
            HtmlButtonElement.stdConfig
          ).getDomElement(),
          span(
            cls := "sorting-interaction__error-count",
            child.text <-- errorCountSignal
          )
        )
      )

    AtomarLineRendering.basicLine(fullInfo, interaction, dom, "sorting-interaction")
  }
}
