package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.sortingExercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

object HtmlSortingInteractionRenderer extends LineBasedRenderingFactory[SortingInteraction] {

  private val errorCountLabelId = LanguageMapContentId("basic/sortingLastErrorCount")

  override protected def createRendering(interaction: SortingInteraction): AtomarLineRendering = {
    val stateVar = interaction.interactionVariable.createBoundVarWithUpdateImportance(fullInfo.syncControl, UpdateImportance.TEMPORARY)

    val errorCountSignal =
      stateVar.signal
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
            interaction.openButtonLabel,
            _ => fullInfo.displayControl.setFullscreen(HtmlSortingExerciseFullscreenElement(interaction, fullInfo)),
            HtmlButtonElement.stdConfig
          ).getDomElement(),
          span(
            cls := "sorting-interaction__error-count",
            child.text <-- errorCountSignal
          )
        )
      )

    AtomarLineRendering.basicLine(interaction, dom, "sorting-interaction")
  }
}
