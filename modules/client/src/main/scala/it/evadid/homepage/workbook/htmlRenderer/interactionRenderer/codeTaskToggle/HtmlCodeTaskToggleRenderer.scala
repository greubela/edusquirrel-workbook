package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

import it.evadid.homepage.webElements.editor.code.FillInCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.workbook.interactions.codeTaskToggle.CodeTaskToggleInteraction
import it.evadid.workbook.sync.UpdateImportance


object HtmlCodeTaskToggleRenderer extends LineBasedRenderingFactory[CodeTaskToggleInteraction] {

  override protected def createRendering(fullInfo: FullInfo, interaction: CodeTaskToggleInteraction): AtomarLineRendering = {
    val stateVar = fullInfo.interactions.boundVar(interaction, UpdateImportance.MINOR)
    val advancedCodeVar: Var[String] = stateVar.zoomLazy(_.advancedCode)((state, code) => state.copy(advancedCode = code))

    def reorderEditor(): Element = div(
      children <-- HtmlReorderInteractionRenderer.renderWorkbookElement(fullInfo, interaction.reorder).rendering.elementsWithoutContainer.allElementsSignal)

    def codeEditor(): Element = FillInCodeEditor(
      fullInfo.loggerSystemInfo.uiAndDomLogger,
      codeState = advancedCodeVar,
      resetTemplate = interaction.advancedCodeTemplate,
      title = laminarHelper(fullInfo).plaintextStringSignal(interaction.codeEditorTitle),
      loadingLabel = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("blockeditor/loading")),
      todoLegend = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/codeTodoLegend")),
      checkButtonLabel = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/checkCode")),
      resetButtonLabel = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/resetCode")),
      resetConfirmMessage = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/resetCodeConfirm")),
      resetConfirmYes = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/resetCodeConfirmYes")),
      resetConfirmNo = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/resetCodeConfirmNo")),
      incompleteIntroId = LanguageMapContentId("basic/advancedCodeFeedbackIncomplete"),
      successMessageId = interaction.advancedSuccessMessage,
      requirements = interaction.advancedRequirements,
      resolveText = laminarHelper(fullInfo).plaintextStringSignal,
      language = interaction.reorder.programmingLanguage
    )

    def modeButton(labelId: String, beginner: Boolean): Element = button(
      cls := "mode-toggle__btn",
      cls("mode-toggle__btn--active") <-- stateVar.signal.map(_.isBeginnerMode == beginner),
      text <-- laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId(labelId)),
      onClick --> { _ => stateVar.update(_.copy(isBeginnerMode = beginner)) }
    )

    // Only the selected editor exists; switching modes creates the other one from the saved answer.
    val dom = div(
      cls := "task-box",
      div(cls := "mode-toggle", modeButton("basic/beginnerMode", beginner = true), modeButton("basic/advancedMode", beginner = false)),
      child <-- stateVar.signal.map(_.isBeginnerMode).distinct.map(beginner => if beginner then reorderEditor() else codeEditor())
    )

    AtomarLineRendering.basicLine(fullInfo, interaction, dom, "code-task-toggle")
  }

}
