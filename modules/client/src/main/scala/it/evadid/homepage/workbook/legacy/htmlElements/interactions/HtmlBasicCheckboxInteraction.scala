package it.evadid.homepage.workbook.legacy.htmlElements.interactions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import it.evadid.core.datastructures.state.*
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.workbook.legacy.model.info.FullInfo

case class HtmlBasicCheckboxInteraction(
                                         fullInfo: FullInfo,
                                         id: String,
                                         labelSignal: Signal[String]
                                       ) extends WorkbookInteraction[Boolean] {

  override val defaultValue: Boolean = false

  override val interactionVariable: InteractionVariable[Boolean] = InteractionVariable[Boolean](this, Serializer.booleanIO)

  private val checkboxVar = interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MAJOR)

  override val serializer: Serializer[Boolean] = Serializer.booleanIO
  
  /*override def getDomElement(): L.Element = {
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
          text <-- labelSignal
        )
      )
    )
  }*/
  
}

object HtmlBasicCheckboxInteraction {
  def apply(fullInfo: FullInfo, id: String, labelLanguageMapId: String): HtmlBasicCheckboxInteraction =
    HtmlBasicCheckboxInteraction(
      fullInfo = fullInfo,
      id = id,
      labelSignal = fullInfo.signals.stringFromLanguageMapId(labelLanguageMapId)
    )
}
