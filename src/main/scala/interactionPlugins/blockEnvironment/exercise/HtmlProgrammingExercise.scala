package interactionPlugins.blockEnvironment.exercise

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class HtmlProgrammingExercise(
    exerciseContent: ProgrammingExercise,
    fullscreenElement: HtmlFullScreenElement
) extends HtmlWorkbookElement {


  val htmlTitleElement: HtmlExerciseTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)
  val htmlInstructionElement: HtmlPlaintextInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  private val currentProgram: Var[BeProgram] = Var(BeProgram(BeProgram.miniProgramExpression()))

  private val previewDisplayConfig: BeTreeDisplayConfig = BeTreeDisplayConfig(true, true, true, true, true)
  private val previewRendererConfig: BeRenderingConfig = BeRenderingConfig.default()
  private val previewControllerConfig: BeTreeControllerConfig = BeTreeControllerConfig.noOpConfig()

  private val expectedPentagonViewBoxStart: Point[Double] = Point[Double](0, 0)

  private val expectedPentagonShape = {
    val startPoint = Point[Double](96.0, 10.0)
    val builder = SvgPathBuilder[Double](startPoint)
      .lineToAbs(Point[Double](168.0, 66.0))
      .lineToAbs(Point[Double](138.0, 166.0))
      .lineToAbs(Point[Double](54.0, 166.0))
      .lineToAbs(Point[Double](24.0, 66.0))
      .closePath()
    builder.toFixedDimensionShape
  }

  private val expectedPentagonElement = {
    val pentagonSize = expectedPentagonShape.displaySize(previewRendererConfig)
    val pentagonBounds = Bounds(expectedPentagonViewBoxStart, pentagonSize)
    val pentagonSvg = expectedPentagonShape
      .render(previewRendererConfig, pentagonBounds)
      .addMods(
        List(
          svg.fill := "#ffe0b2",
          svg.stroke := "#fb8c00",
          svg.strokeWidth := "4"
        )
      )

    div(
      cls := "programming-preview-canvas programming-preview-canvas-svg",
      svg.svg(
        svg.viewBox := s"0 0 ${pentagonSize.width} ${pentagonSize.height}",
        svg.preserveAspectRatio := "xMidYMid meet",
        svg.width := "100%",
        svg.height := "100%",
        pentagonSvg.renderWithMods
      )
    )
  }

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
    fullscreenElement.setElementFullscreen(fullscreenEditor.getDomElement())
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
    expectedPentagonElement
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
