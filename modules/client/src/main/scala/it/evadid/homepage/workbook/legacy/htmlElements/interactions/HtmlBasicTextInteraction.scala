package it.evadid.homepage.workbook.legacy.htmlElements.interactions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, unsafeWindowOwner}
import todomove.webElementsOld.webElements.genericHtmlElements.editor.SimpleTextEditor.TextEditorConfig
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import todomove.webElementsOld.webElements.genericHtmlElements.editor.SimpleTextEditor

case class HtmlBasicTextInteraction(fullInfo: FullInfo, id: String, initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig) extends WorkbookInteraction[String]{

  override val defaultValue: String = ""

  override val interactionVariable: InteractionVariable[String] = InteractionVariable[String](this)

  override val serializer: Serializer[String] = Serializer.stringIO
  
  private val varBoundToEditor: Var[String] = interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MINOR)

  /*
  interactionVariable.interactionSignal.foreach(newStorageValue => {
    val oldValue = varBoundToEditor.now()
    varBoundToEditor.set(newStorageValue)
    //println("new Storage value '" + newStorageValue + "' should overwrite editor value: " + oldValue + " -> " + varBoundToEditor.now())
  })(unsafeWindowOwner)
  varBoundToEditor.signal.foreach(newEditorValue => onUserInputChanged(newEditorValue))(unsafeWindowOwner)*/

  private val curConfig: Var[TextEditorConfig] = Var(initConfig)
  private val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)
  
  /*private def onUserInputChanged(newDisplayedText: String): Unit = {
    val trimmed = newDisplayedText.trim
    val isBigUpdate = trimmed.isEmpty || trimmed.length < newDisplayedText.length || trimmed.endsWith(".")
    val updateType = if (isBigUpdate) UpdateImportance.MINOR else UpdateImportance.TEMPORARY
    interactionVariable.updateStateFromUserInteraction(newDisplayedText, System.currentTimeMillis(), updateType)
  }*/

  private val domElement: L.Element =
    L.div(
      L.cls := "workbook-interaction",
      editor.getDomElement()
    )
  def getDomElement(): L.Element = domElement
  

}
