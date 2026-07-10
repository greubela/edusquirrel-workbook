package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.Var
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.webElements.editor.SimpleTextEditor.TextEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlBasicCheckboxRenderer.fullInfo
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction

object HtmlSimpleTextInteractionRenderer extends LineBasedRenderingFactory[TextInteraction] {

  private val initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig

  override protected def createRendering(workbookElement: TextInteraction): AtomarLineRendering = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateLogic(fullInfo.syncControl,TextInteraction.decideTextareaUpdateImportance).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    AtomarLineRendering.basicLine(workbookElement, editor.getDomElement())
  }
}
