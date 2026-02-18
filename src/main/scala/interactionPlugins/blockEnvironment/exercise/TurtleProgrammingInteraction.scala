package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Bounds
import contentmanagement.model.language.AppLanguage
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import util.Serializer
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.MAJOR
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction

case class TurtleProgrammingInteraction(workbookInfoVar: Var[WorkbookInfo], id: String, expectedSvgResult: AppSvgElement) extends WorkbookInteraction[BeProgram]{

  private val defaultProgram: BeProgram = BeProgram(BeProgram.miniProgramExpression())

  private val io = new Serializer[BeProgram](){
    override def serialize(obj: BeProgram): String = obj.fullProgram.getInLanguage(AppLanguage.Python, AppLanguage.English)
    override def deserialize(str: String): BeProgram = BeProgram.fromPythonString(str)
  }

  override val interactionVariable: InteractionVariable[BeProgram] = InteractionVariable[BeProgram](
    this,
    defaultProgram,
    io)

  private def currentProgram: Var[BeProgram] = interactionVariable.createBoundVarWithUpdateImportance(MAJOR)

  private val previewDisplayConfig: BeTreeDisplayConfig = BeTreeDisplayConfig.previewDefaults
  private val previewRendererConfig: BeRenderingConfig = BeRenderingConfig.default()
  private val previewControllerConfig: BeTreeControllerConfig = BeTreeControllerConfig.noOpConfig()

  private def renderProgramPreview(program: BeProgram): L.HtmlElement = {
    val editorState = EditorState.withGivenVariable(currentProgram)
    val (treeDom, _) = HtmlBeTreeDisplay.render(program, previewDisplayConfig, previewRendererConfig, previewControllerConfig, editorState)
    treeDom.amend(cls := "programming-preview-tree")
  }

  private val openEditorButton: L.HtmlElement = button(
    typ := "button",
    cls := "programming-exercise-action-button",
    "Open full editor",
    onClick --> (_ => openFullEditor())
  )
  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(currentProgram)

  private def openFullEditor(): Unit = {
    //fullscreenEditor.bindToProgram(currentProgram)
    workbookInfoVar.now().fullscreenElement.setElementFullscreen(fullscreenEditor.getDomElement())
  }

  private val expectedPreviewElement = {
    val expectedElement = expectedSvgResult
    val boundingBox: Bounds[Double] = expectedElement.staticBoundingBox
    val width = if (boundingBox.width <= 0) 1.0 else boundingBox.width
    val height = if (boundingBox.height <= 0) 1.0 else boundingBox.height
    val viewBoxStartX = boundingBox.startX
    val viewBoxStartY = boundingBox.startY

    div(
      cls := "programming-preview-canvas programming-preview-canvas-svg",
      svg.svg(
        svg.viewBox := s"$viewBoxStartX $viewBoxStartY $width $height",
        svg.preserveAspectRatio := "xMidYMid meet",
        svg.width := "100%",
        svg.height := "100%",
        expectedElement.renderWithMods
      )
    )
  }

  private def previewCard(title: String, contentMods: L.Modifier[L.HtmlElement]*): L.HtmlElement =
    div(
      cls := "container-interaction-element programming-preview-card",
      div(
        cls := "container-label programming-preview-label",
        title
      ),
      div(
        cls := "container-interaction-element-content programming-preview-content",
        contentMods
      )
    )

  private val programPreviewCard = previewCard(
    title = "Current program",
    div(
      cls := "programming-preview-canvas programming-preview-canvas-tree",
      child <-- currentProgram.signal.map(renderProgramPreview)
    )
  )

  private val expectedPreviewCard = previewCard(
    title = "Expected turtle output",
    expectedPreviewElement
  )

  private val domElement: Element =
    div(
      cls := "container-interaction",
      div(
        cls := "programming-exercise-layout",
        programPreviewCard,
        expectedPreviewCard
      ),
      div(
        cls := "programming-exercise-actions",
        openEditorButton
      )
    )


  override def getDomElement(): L.Element = domElement
}
