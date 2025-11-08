package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Point
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.programming.editor.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import util.Timing


case class HtmlBeTreeDisplay(
                              programToDisplaySignal: Signal[BeProgram],
                              pEditorState: EditorState,
                              pListenerVar: Var[BeTreeControllerConfig],
                              getTreeDisplayConfig: EditorState => Var[BeTreeDisplayConfig]
                            ) {


  def toDomSignal: Signal[L.HtmlElement] = {
    getTreeDisplayConfig(pEditorState).signal
      .combineWith(pEditorState.rendererConfigVar.signal)
      .combineWith(pListenerVar.signal)
      .combineWith(programToDisplaySignal)
      .map(tup => render(tup._4, tup._1, tup._2, tup._3))
  }

  def render(programToDisplay: BeProgram, displayConfig: BeTreeDisplayConfig, rendererConfig: BeRenderingConfig, listener: BeTreeControllerConfig): L.HtmlElement = {
    val tree: BeBlockRenderingTree = programToDisplay.blockRenderingTree(displayConfig)
    val posToDraw = tree.getChildren(tree.rootPosition)

    val renderingInfo = RenderingInformation(programToDisplay, displayConfig, rendererConfig, listener, pEditorState.controllerStateVar)

    val renderedTree = Timing.executeAndTime(
      () => tree.mapWithContext[NestedBlockRenderer](curStructure => curStructure.curValue._2.render(curStructure, renderingInfo)),
      "time to render tree")

    val nestedBlockRenderer = renderedTree.getData(renderedTree.rootPosition.forChild(0)).get
    val shapeToDraw = Timing.executeAndTime(() => {
      if (displayConfig.displayControlFlow) {
        val controlFlowBackground = nestedBlockRenderer.controlFlowBackgroundShape
        val controlFlowOverlay = nestedBlockRenderer.controlFlowOverlayShape(renderingInfo)
        val shapes = List(controlFlowBackground, controlFlowOverlay)
        ShapeStack(shapes, fixedRelativeOffset = Map(controlFlowBackground -> Point[Double](0, 0), controlFlowOverlay -> Point[Double](0, 0)))
      } else {
        nestedBlockRenderer.expressionShapeWithoutIntendation
      }
    }, "time to build shape")

    val svgDomElement = {

      val displaySize = shapeToDraw.displaySize(rendererConfig)
      val rendered = shapeToDraw.render(rendererConfig, Point[Double](0, 0).withDimension(displaySize))

      val svgCanvas: SvgCanvas = new SvgCanvas(displaySize.width.toInt, displaySize.height.toInt)
      svgCanvas.addSvgElement(rendered.renderWithMods)
      div(
        svgCanvas.getDomElement()
      ).amend(listener.getHtmlDragAmends(programToDisplay))
    }

    svgDomElement


  }


}
