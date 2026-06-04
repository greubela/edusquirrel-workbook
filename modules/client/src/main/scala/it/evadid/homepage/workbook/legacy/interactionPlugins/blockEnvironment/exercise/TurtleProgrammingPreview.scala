package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Bounds
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.info.{FullInfo, HomepageInfo}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeTreeDisplay}
import todomove.webElementsOld.webElements.svg.AppSvgElement

case class TurtleProgrammingPreview(fullInfo: FullInfo, editorState: EditorState, expectedSvgResult: AppSvgElement) extends HtmlAppElement {

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

  private def previewCard(cardType: String, cardLabelMapId: String, cardContent: Signal[Element]): Element = div(
    cls := "preview-card",
    h3(
      text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(cardLabelMapId))
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
      previewCard("program", "BlockEditor/yourProgram", editorState.treeToEdit.signal.map(renderProgramPreview)),
      previewCard("output", "BlockEditor/programOutcome", Var(div(renderedSvg)).signal),
    )

  override def getDomElement(): Element = domElement
}
