package workbook.htmlElements.interactions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import util.serializing.Serializer
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.FullInfo
import workbook.model.interaction.InteractionVariable

import scala.concurrent.ExecutionContext

case class HtmlBasicCheckboxInteraction(
                                         fullInfo: FullInfo,
                                         id: String,
                                         labelSignal: Signal[String]
                                       ) extends WorkbookInteraction[Boolean] {

  override val defaultValue: Boolean = false
  
  override val interactionVariable: InteractionVariable[Boolean] = InteractionVariable[Boolean](this, Serializer.booleanIO)

  private val checkboxVar = interactionVariable.createBoundVarWithUpdateImportance(
    workbook.model.interaction.history.UpdateImportance.MAJOR
  )

  override def getDomElement(): L.Element = {
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
  }
}

object HtmlBasicCheckboxInteraction {
  def apply(fullInfo: FullInfo, id: String, labelLanguageMapId: String): HtmlBasicCheckboxInteraction =
    HtmlBasicCheckboxInteraction(
      fullInfo = fullInfo,
      id = id,
      labelSignal = fullInfo.signals.stringFromLanguageMapId(labelLanguageMapId)
    )
}
