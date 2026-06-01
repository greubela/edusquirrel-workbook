package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.basic.LabeledCheckboxInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance

object HtmlBasicCheckboxRenderer extends HtmlRenderFactory[LabeledCheckboxInteraction] {

  override def createDomElement(lci: LabeledCheckboxInteraction): L.Element = {

    val checkboxVar = lci.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR).toAirstreamVar

    div(
      cls := "workbook-interaction simple-boolean-editor",
      label(
        cls := "simple-boolean-editor__body",
        input(
          typ := "checkbox",
          cls := "simple-boolean-editor__checkbox",
          controlled(
            checked <-- checkboxVar.signal,
            onInput.mapToChecked --> checkboxVar.writer
          )
        ),
        span(
          cls := "simple-boolean-editor__label-text",
          text <-- contentIdStringSignal(lci.checkboxLabel)
        )
      )
    )
  }


}

