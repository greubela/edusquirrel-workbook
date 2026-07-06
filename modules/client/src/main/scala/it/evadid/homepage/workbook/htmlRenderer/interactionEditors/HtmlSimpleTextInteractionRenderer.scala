package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.webElements.editor.SimpleTextEditor.TextEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, HtmlWorkbookElement}
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import org.scalajs.dom.HTMLDivElement

object HtmlSimpleTextInteractionRenderer extends LineBasedRenderingFactory[TextInteractionBasic] {

  private val initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig

  override protected def createRendering(workbookElement: TextInteractionBasic): AtomarLineRendering = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateLogic(WorkbookInteraction.decideTextareaUpdateImportance).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    RenderingLine(true,  editor.getDomElement())
  }
}
