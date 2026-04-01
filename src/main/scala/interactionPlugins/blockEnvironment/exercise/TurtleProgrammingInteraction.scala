package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.Bounds
import datastructures.core.language.AppLanguage
import interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig, BlockEnvironmentLanguageMap}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import util.serializing.Serializer
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.MAJOR
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}

case class TurtleProgrammingInteraction(workbookInfo: AllWorkbookInfo, id: String, expectedSvgResult: AppSvgElement) extends WorkbookInteraction[BeProgram] {

  private val defaultProgram: BeProgram = BeProgram(BeProgram.miniProgramExpression())

  private val io = new Serializer[BeProgram]() {
    override def serialize(obj: BeProgram): String = obj.fullProgram.getInLanguage(AppLanguage.Python, AppLanguage.English)

    override def deserialize(str: String): BeProgram = BeProgram.fromPythonString(str)
  }

  override val interactionVariable: InteractionVariable[BeProgram] = InteractionVariable[BeProgram](
    this,
    defaultProgram,
    io)

  private val editorState: EditorState = {
    val initRenderer = BeRenderingConfig.defaultWithLanguage(workbookInfo.workbookInfoVar.now().config.currentWorkbookLanguage)
    val rendererVar = Var(initRenderer)

    workbookInfo.workbookInfoVar.signal.foreach(onNext => rendererVar.update(_.copy(language = onNext.config.currentWorkbookLanguage)))(unsafeWindowOwner)

    val initControllerState: BeEditorControllerState = BeEditorControllerState.default()
    EditorState(
      interactionVariable.createBoundVarWithUpdateImportance(MAJOR),
      Var(initControllerState),
      rendererVar
    )
  }

  private val openEditorButton = TurtleProgrammingOpenEditorButton(workbookInfo, editorState)
  private val programmingView = TurtleProgrammingPreview(workbookInfo, editorState, expectedSvgResult)

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
