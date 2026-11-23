package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object WorkbookContentChoiceInteractionRenderer extends LineBasedRenderingFactory[WorkbookContentChoiceInteraction] {
  override protected def createRendering(e: WorkbookContentChoiceInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val controls = fieldSet(cls := "choice-interaction",
      legend(InlineMarkdown.element(laminarHelper.plaintextStringSignal(e.config.instruction))),
      e.options.zipWithIndex.map { (option, index) => label(
        input(typ := (if e.config.allowMultipleSelected.contains(true) then "checkbox" else "radio"), nameAttr := e.elementId,
          checked <-- state.signal.map(_.selectedOptions.contains(option)),
          disabled <-- e.isDisabledState.toAirstreamVar.signal,
          onChange.mapToChecked --> (selected => state.update(e.select(_, index, selected)))),
        InlineMarkdown.element(laminarHelper.workbookContentSignal(option.content))) }
    )
    AtomarLineRendering.basicLine(e, controls)
  }
}
