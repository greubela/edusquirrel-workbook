package interactionPlugins.blockEnvironment.exercise

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.Bounds
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class HtmlProgrammingExercise(
    exerciseContent: ProgrammingExercise,
    fullscreenElement: HtmlFullScreenElement
) extends HtmlWorkbookElement {


  val htmlTitleElement: HtmlExerciseTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)
  val htmlInstructionElement: HtmlPlaintextInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  private val currentProgram: Var[BeProgram] = Var(BeProgram(BeProgram.miniProgramExpression()))

  private val previewDisplayConfig: BeTreeDisplayConfig = BeTreeDisplayConfig.previewDefaults
  private val previewRendererConfig: BeRenderingConfig = BeRenderingConfig.default()
  private val previewControllerConfig: BeTreeControllerConfig = BeTreeControllerConfig.noOpConfig()

  private def renderProgramPreview(program: BeProgram): L.HtmlElement = {
    val editorState = EditorState.withInitExpression(program.fullProgram)
    val (treeDom, _) = HtmlBeTreeDisplay.render(program, previewDisplayConfig, previewRendererConfig, previewControllerConfig, editorState)
    treeDom.amend(cls := "programming-preview-tree")
  }

  private val openEditorButton: L.HtmlElement = button(
    typ := "button",
    cls := "programming-exercise-action-button",
    "Open full editor",
    onClick --> (_ => openFullEditor())
  )

  private def openFullEditor(): Unit = {
    val fullscreenEditor = HtmlFullscreenTurtleEditorElement(currentProgram.now().fullProgram)
    fullscreenEditor.bindToProgram(currentProgram)
    fullscreenElement.setElementFullscreen(fullscreenEditor.getDomElement())
  }

  private val expectedPreviewElement = {
    val expectedElement = exerciseContent.expectedResult
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

  private val domElement: Element = div(
    cls := "container-exercise style-vbox programming-exercise",

    htmlTitleElement.getDomElement(),
    htmlInstructionElement.getDomElement(),
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
  )

  override def getDomElement(): L.Element = domElement


}
