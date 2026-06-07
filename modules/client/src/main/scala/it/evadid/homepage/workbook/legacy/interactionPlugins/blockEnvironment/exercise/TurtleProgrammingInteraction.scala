package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements.EditorState
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import todomove.webElementsOld.webElements.svg.AppSvgElement

case class TurtleProgrammingInteraction(fullInfo: FullInfo, id: String, expectedSvgResult: AppSvgElement) extends WorkbookInteraction[BeProgram] {

  val defaultValue: BeProgram = BeProgram(BeProgram.miniProgramExpression())

  private val io = new Serializer[BeProgram]() {
    override def serialize(obj: BeProgram): String = obj.fullProgram.expressionIO.getInLanguage(AppLanguage.Python, AppLanguage.English, false)

    override def deserialize(str: String): BeProgram = BeProgram.fromPythonString(str)
  }

  override val serializer: Serializer[BeProgram] = io

  private val boundVar: Var[BeProgram] = interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR).toAirstreamVar

  private val editorState: EditorState = {
    val initRenderer = BeRenderingConfig.defaultWithLanguage(fullInfo.signals.currentLanguage.now())
    val rendererVar = Var(initRenderer)

    fullInfo.signals.currentLanguage.foreach(currentLanguage => rendererVar.update(_.copy(language = currentLanguage)))(using unsafeWindowOwner)

    val initControllerState: BeEditorControllerState = BeEditorControllerState.default()
    EditorState(
      boundVar,
      Var(initControllerState),
      rendererVar
    )
  }

  private val openEditorButton = HtmlButtonElement.withTextLabel(LanguageMapContentId("BlockEditor/openEditor"), _ => openFullEditor())
  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(editorState)

  private def openFullEditor(): Unit = {
    fullInfo.technical.makeFullscreen(fullscreenEditor)
  }

  private val programmingView = TurtleProgrammingPreview(fullInfo, editorState, expectedSvgResult)

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


  // override def getDomElement(): L.Element = domElement


}
