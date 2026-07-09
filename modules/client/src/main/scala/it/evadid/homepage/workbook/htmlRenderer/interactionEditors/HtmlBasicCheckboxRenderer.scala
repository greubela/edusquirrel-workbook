package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import org.scalajs.dom.HTMLLabelElement

object HtmlBasicCheckboxRenderer extends LineBasedRenderingFactory[LabeledCheckboxInteraction] {


  override protected def createRendering(lci: LabeledCheckboxInteraction): AtomarLineRendering = {
    val checkboxVar: Var[Boolean] = lci.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR).toAirstreamVar

    val dom: ReactiveHtmlElement[HTMLLabelElement] =
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

    RenderingLine(true, dom, "simple-boolean-editor")
  }
}

