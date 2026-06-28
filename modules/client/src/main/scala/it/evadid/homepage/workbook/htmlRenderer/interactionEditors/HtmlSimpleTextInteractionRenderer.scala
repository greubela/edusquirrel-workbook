package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import SimpleTextEditor.TextEditorConfig
import it.evadid.workbook.model.interaction.WorkbookInteraction

object HtmlSimpleTextInteractionRenderer extends HtmlRenderFactory[TextInteractionBasic] {

  private val initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig
    
  override def createDomElement(workbookElement: TextInteractionBasic): L.Element = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateLogic(WorkbookInteraction.decideTextareaUpdateImportance).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    L.div(
      L.cls := "workbook-interaction",
      editor.getDomElement()
    )
  }


}
