package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object WorkbookContentChoiceInteractionRenderer extends LineBasedRenderingFactory[WorkbookContentChoiceInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: WorkbookContentChoiceInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(e, UpdateImportance.MAJOR)
    // Options that a failed check found wrong are marked until the answer changes.
    val wrong = fullInfo.reporters.reportersFor(e).grading.checks.map(_.wrongParts).distinct
    val controls = fieldSet(cls := "choice-interaction",
      legend(InlineMarkdown.element(laminarHelper(fullInfo).plaintextStringSignal(e.config.instruction))),
      e.options.zipWithIndex.map { (option, index) => label(
        cls("answer-part--wrong") <-- wrong.map(_.contains(index)),
        input(typ := (if e.config.allowMultipleSelected.contains(true) then "checkbox" else "radio"), nameAttr := e.elementId,
          checked <-- state.signal.map(_.selectedOptions.contains(option)),
          disabled <-- e.isDisabledState.toAirstreamVar.signal,
          onChange.mapToChecked --> (selected => state.update(e.select(_, index, selected)))),
        InlineMarkdown.element(laminarHelper(fullInfo).workbookContentSignal(option.content))) }
    )
    AtomarLineRendering.basicLine(fullInfo, e, controls)
  }
}
