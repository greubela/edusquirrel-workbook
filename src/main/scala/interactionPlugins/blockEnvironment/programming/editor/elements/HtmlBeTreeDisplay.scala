package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Point
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

import scala.collection.mutable



case class HtmlBeTreeDisplay(
                              treeToDisplaySignal: Signal[BeProgram],
                              pEditorState: TreeEditorState,
                              pListenerVar: Var[BeTreeControllerConfig]
                            ) {


  def toDomSignal: Signal[L.HtmlElement] = {
    pEditorState.displayConfigVar.signal
      .combineWith(pEditorState.rendererConfigVar.signal)
      .combineWith(pListenerVar.signal)
      .combineWith(treeToDisplaySignal)
      .map(tup => render(tup._4, tup._1, tup._2, tup._3))
  }

  def render(programToDisplay: BeProgram, displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, listener: BeTreeControllerConfig): L.HtmlElement = {
    val tree: BeBlockTree = programToDisplay.blockTree
    val posToDraw = tree.getChildren(tree.rootPosition)

    val finishedRenderings: mutable.ListBuffer[AppSvgElement] = mutable.ListBuffer[AppSvgElement]()
    tree.foreachWithStructure((structure: BeBlockContext) => {
      if (posToDraw.contains(structure.curPosition)) {
        val finishedShape: BeShape = structure.curValue.render(programToDisplay, listener, pEditorState.controllerStateVar, displayConfig, rendererConfig, structure)
        val svg: AppSvgElement = finishedShape.render(rendererConfig, Point[Double](0, 0).withDimension(finishedShape.displaySize(rendererConfig)))
        finishedRenderings += svg
      }
    })

    val svgDomElement = finishedRenderings.map(treeSvgElement => {
      val svgDim = treeSvgElement.staticBoundingBox.dimension.increaseSize(rendererConfig.paddingBig)
      val svgCanvas: SvgCanvas = new SvgCanvas(svgDim.width.toInt, svgDim.height.toInt)
      svgCanvas.addSvgElement(treeSvgElement.renderWithMods)
      div(
        svgCanvas.getDomElement()
      ).amend(listener.getHtmlDragAmends(programToDisplay))
    })

    val alt = div(
      cls := "altTreeDisplay",
      "no positions to draw: " + posToDraw
    )

    svgDomElement.headOption.getOrElse(alt)


  }


}

object HtmlBeTreeDisplay {

  // Todo: Basic Shape from BeBlock, but Amends for graphic here in Display

}