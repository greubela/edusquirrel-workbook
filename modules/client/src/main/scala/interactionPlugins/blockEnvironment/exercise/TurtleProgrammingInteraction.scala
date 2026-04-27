package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.EditorState
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.util.io.Serializer
import workbook.htmlElements.basic.HtmlButtonElement
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.FullInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.MAJOR

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
case class TurtleProgrammingInteraction(fullInfo: FullInfo, id: String, expectedSvgResult: AppSvgElement) extends WorkbookInteraction[BeProgram] {

  val defaultValue: BeProgram = BeProgram(BeProgram.miniProgramExpression())

  private val io = new Serializer[BeProgram]() {
    override def serialize(obj: BeProgram): String = obj.fullProgram.getInLanguage(AppLanguage.Python, AppLanguage.English)

    override def deserialize(str: String): BeProgram = BeProgram.fromPythonString(str)
  }

  override val interactionVariable: InteractionVariable[BeProgram] = InteractionVariable[BeProgram](
    this,
    io)

  private val editorState: EditorState = {
    val initRenderer = BeRenderingConfig.defaultWithLanguage(fullInfo.signals.currentLanguage.now())
    val rendererVar = Var(initRenderer)

    fullInfo.signals.currentLanguage.foreach(currentLanguage => rendererVar.update(_.copy(language = currentLanguage)))(unsafeWindowOwner)

    val initControllerState: BeEditorControllerState = BeEditorControllerState.default()
    EditorState(
      interactionVariable.createBoundVarWithUpdateImportance(MAJOR),
      Var(initControllerState),
      rendererVar
    )
  }

  private val openEditorButton = HtmlButtonElement.withTextLabel(fullInfo, "BlockEditor/openEditor", _ => openFullEditor())
  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(editorState)

  private def openFullEditor(): Unit = {
    fullInfo.technical.makeFullscreen(fullscreenEditor.getDomElement())
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


  override def getDomElement(): L.Element = domElement
}
