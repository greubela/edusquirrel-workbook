package workbook.htmlElements.interactions

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.editor.SimpleTextEditor.TextEditorConfig
import contentmanagement.webElements.genericHtmlElements.editor.*
import util.serializing.Serializer
import workbook.htmlElements.interactions.HtmlBasicTextInteraction.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance

import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L.unsafeWindowOwner
import workbook.model.info.{FullInfo, HomepageInfo}

case class HtmlBasicTextInteraction(fullInfo: FullInfo, id: String, initConfig: TextEditorConfig = SimpleTextEditor.defaultConfig) extends WorkbookInteraction[String]{

  override val defaultValue: String = ""

  private val varBoundToEditor: Var[String] = Var(defaultValue)
  override val interactionVariable: InteractionVariable[String] = InteractionVariable[String](this, Serializer.stringIO)

  interactionVariable.interactionSignal.foreach(newStorageValue => {
    val oldValue = varBoundToEditor.now()
    varBoundToEditor.set(newStorageValue)
    //println("new Storage value '" + newStorageValue + "' should overwrite editor value: " + oldValue + " -> " + varBoundToEditor.now())
  })(unsafeWindowOwner)
  varBoundToEditor.signal.foreach(newEditorValue => onUserInputChanged(newEditorValue))(unsafeWindowOwner)

  private val curConfig: Var[TextEditorConfig] = Var(initConfig)
  private val editor: SimpleTextEditor = SimpleTextEditor(varBoundToEditor, curConfig)
  
  
  
  private def onUserInputChanged(newDisplayedText: String): Unit = {
    val trimmed = newDisplayedText.trim
    val isBigUpdate = trimmed.isEmpty || trimmed.length < newDisplayedText.length || trimmed.endsWith(".")
    val updateType = if (isBigUpdate) UpdateImportance.MINOR else UpdateImportance.TEMPORARY
    interactionVariable.updateStateFromUserInteraction(newDisplayedText, System.currentTimeMillis(), updateType)
  }

  private val domElement: L.Element =
    L.div(
      L.cls := "workbook-interaction",
      editor.getDomElement()
    )
  override def getDomElement(): L.Element = domElement
  

}
