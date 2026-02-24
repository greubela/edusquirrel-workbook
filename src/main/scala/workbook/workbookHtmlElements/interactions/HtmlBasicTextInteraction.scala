package workbook.workbookHtmlElements.interactions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.webElements.genericHtmlElements.editor.SimpleStringExerciseVariableTextEditor
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction

case class HtmlBasicTextInteraction(workbookInfoVar: Var[WorkbookInfo], id: String) extends WorkbookInteraction[String]{

  override val interactionVariable: InteractionVariable[String] = InteractionVariable.stringVariable(this, "")

  private val solutionEditor = SimpleStringExerciseVariableTextEditor(interactionVariable)

  private val domElement: L.Element = solutionEditor.getDomElement()

  override def getDomElement(): L.Element = domElement

}
