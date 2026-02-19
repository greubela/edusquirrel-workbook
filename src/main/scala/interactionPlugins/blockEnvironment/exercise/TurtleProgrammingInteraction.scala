package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Bounds
import contentmanagement.model.language.AppLanguage
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig, BlockEnvironmentLanguageMap}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import util.Serializer
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.MAJOR
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction

case class TurtleProgrammingInteraction(workbookInfoVar: Var[WorkbookInfo], id: String, expectedSvgResult: AppSvgElement) extends WorkbookInteraction[BeProgram] {

  private val defaultProgram: BeProgram = BeProgram(BeProgram.miniProgramExpression())

  private val io = new Serializer[BeProgram]() {
    override def serialize(obj: BeProgram): String = obj.fullProgram.getInLanguage(AppLanguage.Python, AppLanguage.English)

    override def deserialize(str: String): BeProgram = BeProgram.fromPythonString(str)
  }

  override val interactionVariable: InteractionVariable[BeProgram] = InteractionVariable[BeProgram](
    this,
    defaultProgram,
    io)

  private val currentProgram: Var[BeProgram] = interactionVariable.createBoundVarWithUpdateImportance(MAJOR)

  private val openEditorButton = TurtleProgrammingOpenEditorButton(workbookInfoVar, currentProgram)
  private val programmingView = TurtleProgrammingPreview(workbookInfoVar, currentProgram, expectedSvgResult)

  private val domElement: Element =
    div(
      cls := "workbook-interaction programming-exercise",
      div(
        cls := "programming-exercise-preview",
        programmingView.getDomElement()
      ),
      div(
        cls := "programming-exercise-button",
        openEditorButton.getDomElement()
      )
    )


  override def getDomElement(): L.Element = domElement
}
