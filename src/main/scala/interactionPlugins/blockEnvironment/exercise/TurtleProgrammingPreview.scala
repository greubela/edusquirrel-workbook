package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.api.L.svg
import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.Bounds
import datastructures.core.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig, BlockEnvironmentLanguageMap}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.model.interaction.InteractionVariable

case class TurtleProgrammingPreview(workbookInfo: AllWorkbookInfo, editorState: EditorState, expectedSvgResult: AppSvgElement) extends HtmlWorkbookElement {

  private val renderedSvg: SvgElement = {
    val expectedElement = expectedSvgResult
    val boundingBox: Bounds[Double] = expectedElement.staticBoundingBox
    val width = if (boundingBox.width <= 0) 1.0 else boundingBox.width
    val height = if (boundingBox.height <= 0) 1.0 else boundingBox.height
    val viewBoxStartX = boundingBox.startX
    val viewBoxStartY = boundingBox.startY
    svg.svg(
      svg.viewBox := s"$viewBoxStartX $viewBoxStartY $width $height",
      svg.preserveAspectRatio := "xMidYMid meet",
      svg.width := "100%",
      svg.height := "100%",
      expectedElement.renderWithMods
    )
  }

  private def renderProgramPreview(program: BeProgram): Element = {
    HtmlBeTreeDisplay(
      editorState,
      Var(program).signal,
      Var(BeTreeDisplayConfig.previewDefaults).signal,
      editorState.rendererConfigVar.signal,
      Var(BeTreeControllerConfig.noOpConfig()).signal
    ).treeInContainerDiv
   // val (treeDom, _) = HtmlBeTreeDisplay.render(program, previewDisplayConfig, editorState.rendererConfigVar., previewControllerConfig, editorState)
    //treeDom.amend(cls := "programming-preview-tree")
  }

  private def previewCard(cardType: String, cardLabel: LanguageMap[HumanLanguage], cardContent: Signal[Element]): Element = div(
    cls := "preview-card",
    h3(
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(cardLabel.getInLanguage)
    ),
    div(
      cls := "preview-content",
      child <-- cardContent
    )
  )

  private val domElement: Element =
    div(
      cls := "workbook-interaction preview-line",
      //
      previewCard("program", BlockEnvironmentLanguageMap.languageMapYourProgram, editorState.treeToEdit.signal.map(renderProgramPreview)),
      previewCard("output", BlockEnvironmentLanguageMap.languageMapProgramOutcome, Var(div(renderedSvg)).signal),
    )

  override def getDomElement(): Element = domElement
}
