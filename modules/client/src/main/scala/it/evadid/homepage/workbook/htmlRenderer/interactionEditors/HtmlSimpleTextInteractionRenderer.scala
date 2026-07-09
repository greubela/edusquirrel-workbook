package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.Var
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.webElements.editor.SimpleTextEditor.TextEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction

object HtmlSimpleTextInteractionRenderer extends LineBasedRenderingFactory[TextInteraction] {

  private val initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig

  override protected def createRendering(workbookElement: TextInteraction): AtomarLineRendering = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateLogic(TextInteraction.decideTextareaUpdateImportance).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    RenderingLine(true, editor.getDomElement())
  }
}
