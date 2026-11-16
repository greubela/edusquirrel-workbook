package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.{Var, Val}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.webElements.editor.config.TextEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlBasicCheckboxRenderer.fullInfo
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction2

object HtmlSimpleTextInteractionRenderer extends LineBasedRenderingFactory[TextInteraction2] {

  private val initConfig: TextEditorConfig = TextEditorConfig.defaultConfig

  override protected def createRendering(workbookElement: TextInteraction2): AtomarLineRendering = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateLogic(TextInteraction2.decideTextareaUpdateImportance, fullInfo.workbookContextInformation).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    AtomarLineRendering.basicLine(workbookElement, editor.getDomElement(), "simple-text-editor")
  }
}
