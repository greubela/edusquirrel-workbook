package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import todomove.webElementsOld.webElements.genericHtmlElements.editor.SimpleTextEditor
import todomove.webElementsOld.webElements.genericHtmlElements.editor.SimpleTextEditor.TextEditorConfig

object HtmlSimpleTextInteractionRenderer extends HtmlRenderFactory[TextInteractionBasic] {

  private val initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig

  override def createDomElement(workbookElement: TextInteractionBasic): L.Element = {
    val varBoundToEditor: Var[String] = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar
    val curConfig: Var[TextEditorConfig] = Var(initConfig)
    val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)

    L.div(
      L.cls := "workbook-interaction",
      editor.getDomElement()
    )
  }

  /*
  interactionVariable.interactionSignal.foreach(newStorageValue => {
    val oldValue = varBoundToEditor.now()
    varBoundToEditor.set(newStorageValue)
    //println("new Storage value '" + newStorageValue + "' should overwrite editor value: " + oldValue + " -> " + varBoundToEditor.now())
  })(unsafeWindowOwner)
  varBoundToEditor.signal.foreach(newEditorValue => onUserInputChanged(newEditorValue))(unsafeWindowOwner)*/


  /*private def onUserInputChanged(newDisplayedText: String): Unit = {
    val trimmed = newDisplayedText.trim
    val isBigUpdate = trimmed.isEmpty || trimmed.length < newDisplayedText.length || trimmed.endsWith(".")
    val updateType = if (isBigUpdate) UpdateImportance.MINOR else UpdateImportance.TEMPORARY
    interactionVariable.updateStateFromUserInteraction(newDisplayedText, System.currentTimeMillis(), updateType)
  }*/

}
