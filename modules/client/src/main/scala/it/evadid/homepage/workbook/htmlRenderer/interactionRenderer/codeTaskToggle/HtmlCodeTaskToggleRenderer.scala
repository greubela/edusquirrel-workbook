package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.legacy.plantworkshop.helpers.CodeEditorHelper
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.CodeTaskToggleInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

object HtmlCodeTaskToggleRenderer extends LineBasedRenderingFactory[CodeTaskToggleInteraction] {

  override protected def createRendering(interaction: CodeTaskToggleInteraction): AtomarLineRendering = {
    val stateVar = interaction.interactionVariable.createBoundVarWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MINOR)
    val advancedCodeVar: Var[String] = Var(stateVar.now().advancedCode)

    val reorderDom =
      HtmlReorderInteractionRenderer
        .renderWorkbookElement(interaction.reorder)
        .rendering
        .elementsWithoutContainer

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeVar,
      interaction.codeEditorTitle,
      _ => "Die automatische Code-Prüfung ist noch nicht verfügbar."
    )

    def setBeginnerMode(value: Boolean): Unit = {
      interaction.interactionVariable.updateStateFromUserInteraction(
        fullInfo.syncControl,
        state => state.copy(isBeginnerMode = value),
        UpdateImportance.MINOR
      )
    }

    val dom = div(
      cls := "task-box",
      onMountUnmountCallback(
        mount = { ctx =>
          advancedCodeVar.set(stateVar.now().advancedCode)
          val _ = advancedCodeVar.signal.foreach { code =>
            if (code != stateVar.now().advancedCode) {
              interaction.interactionVariable.updateStateFromUserInteraction(
                fullInfo.syncControl,
                state => state.copy(advancedCode = code),
                UpdateImportance.MINOR
              )
            }
          }(using ctx.owner)
        },
        unmount = _ => ()
      ),
      div(
        cls := "mode-toggle",
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- stateVar.signal.map(_.isBeginnerMode),
          text <-- laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/beginnerMode")),
          onClick --> { _ => setBeginnerMode(true) }
        ),
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- stateVar.signal.map(state => !state.isBeginnerMode),
          text <-- laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/advancedMode")),
          onClick --> { _ => setBeginnerMode(false) }
        )
      ),
      div(
        cls.toggle("mode-hidden") <-- stateVar.signal.map(state => !state.isBeginnerMode),
        children <-- reorderDom.allElementsSignal
      ),
      div(
        cls.toggle("mode-hidden") <-- stateVar.signal.map(_.isBeginnerMode),
        codeEditor
      )
    )

    AtomarLineRendering.basicLine(interaction, dom, "code-task-toggle")
  }

}
