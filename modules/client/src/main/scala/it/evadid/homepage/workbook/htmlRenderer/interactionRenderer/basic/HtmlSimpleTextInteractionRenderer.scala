package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.{Var, Val}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.webElements.editor.config.TextEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction

object HtmlSimpleTextInteractionRenderer extends LineBasedRenderingFactory[TextInteraction] {

  private val initConfig: TextEditorConfig = TextEditorConfig.defaultConfig

  override protected def createRendering(fullInfo: FullInfo, workbookElement: TextInteraction): AtomarLineRendering = {
    val varBoundToEditor: Var[String] = fullInfo.interactions.boundVar(workbookElement, TextInteraction.decideTextareaUpdateImportance)
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(fullInfo, varBoundToEditor, curConfig)

    AtomarLineRendering.basicLine(fullInfo, workbookElement, editor.getDomElement(), "simple-text-editor")
  }
}
