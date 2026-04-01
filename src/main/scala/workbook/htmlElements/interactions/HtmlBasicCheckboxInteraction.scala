package workbook.htmlElements.interactions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.AllWorkbookInfo
import workbook.model.interaction.InteractionVariable

import scala.concurrent.ExecutionContext

case class HtmlBasicCheckboxInteraction(
                                         workbookInfo: AllWorkbookInfo,
                                         id: String,
                                         labelSignal: Signal[String]
                                       ) extends WorkbookInteraction[Boolean] {

  override val interactionVariable: InteractionVariable[Boolean] = InteractionVariable.booleanVariable(this, false)

  private val checkboxVar = interactionVariable.createBoundVarWithUpdateImportance(
    workbook.model.interaction.history.UpdateImportance.MAJOR
  )

  override def getDomElement(): L.Element = {
    div(
      cls := "simple-boolean-editor",
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
  def apply(workbookInfo: AllWorkbookInfo, id: String, labelLanguageMapId: String): HtmlBasicCheckboxInteraction =
    HtmlBasicCheckboxInteraction(
      workbookInfo = workbookInfo,
      id = id,
      labelSignal = workbookInfo.stringSignalFromLanguageMapId(labelLanguageMapId)(ExecutionContext.global)
    )
}
