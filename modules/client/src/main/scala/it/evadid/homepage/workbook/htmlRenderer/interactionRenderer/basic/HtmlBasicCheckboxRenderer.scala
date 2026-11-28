package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.interactions.basic.LabeledCheckboxInteraction
import it.evadid.workbook.sync.UpdateImportance
import org.scalajs.dom.HTMLLabelElement

object HtmlBasicCheckboxRenderer extends LineBasedRenderingFactory[LabeledCheckboxInteraction] {

  override protected def createRendering(fullInfo: FullInfo, lci: LabeledCheckboxInteraction): AtomarLineRendering = {
    val checkboxVar: Var[Boolean] = fullInfo.interactions.boundVar(lci, UpdateImportance.MAJOR)

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
          text <-- laminarHelper(fullInfo).plaintextStringSignal(lci.checkboxLabel)
        )
      )

    AtomarLineRendering.basicLine(fullInfo, lci, dom, "simple-boolean-editor")
  }

}

