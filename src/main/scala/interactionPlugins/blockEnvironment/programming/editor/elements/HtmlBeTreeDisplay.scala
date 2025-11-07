package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Point
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeTreeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.{NestedBlockRenderer, RenderingInformation}
import interactionPlugins.blockEnvironment.programming.editor.*

import scala.collection.mutable


case class HtmlBeTreeDisplay(
                              programToDisplaySignal: Signal[BeProgram],
                              pEditorState: TreeEditorState,
                              pListenerVar: Var[BeTreeControllerConfig],
                              getTreeDisplayConfig: TreeEditorState => Var[BeTreeDisplayConfig]
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

    val renderedTree = tree.mapWithContext[NestedBlockRenderer](curStructure => curStructure.curValue._2.render(curStructure, renderingInfo))

    val nestedBlockRenderer = renderedTree.getData(renderedTree.rootPosition.forChild(0)).get
    val shapeToDraw = if (displayConfig.displayControlFlow) {
        nestedBlockRenderer.getShapeControlFlowAndExpressions
    }else{
      nestedBlockRenderer.getShapeExpressions
    }

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
