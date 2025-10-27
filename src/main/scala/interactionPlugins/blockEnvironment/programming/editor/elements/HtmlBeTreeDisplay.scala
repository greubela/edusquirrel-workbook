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
                              pListenerVar: Var[HtmlBeTreeListener]
                            ) {

  def toDomSignal: Signal[L.HtmlElement] = {
    pEditorState.controllerStateVar.signal
      .combineWith(pEditorState.displayConfigVar.signal)
      .combineWith(pEditorState.rendererConfigVar.signal)
      .combineWith(pListenerVar.signal)
      .combineWith(treeToDisplaySignal)
      .map(tup => render(tup._5, tup._2, tup._3, tup._4))
  }

  def render(programToDisplay: BeProgram, displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, listener: HtmlBeTreeListener): L.HtmlElement = {
    val tree: BeBlockTree = programToDisplay.blockTree
    val posToDraw = tree.getChildren(tree.rootPosition)

    val finishedRenderings: mutable.ListBuffer[AppSvgElement] = mutable.ListBuffer[AppSvgElement]()
    tree.foreachWithStructure((structure: BeBlockContext) => {
      if (posToDraw.contains(structure.curPosition)) {
        val finishedShape: BeShape = structure.curValue.render(programToDisplay, pEditorState.controllerStateVar, displayConfig, rendererConfig, structure)
        val svg: AppSvgElement = finishedShape.render(rendererConfig, Point[Double](0, 0).withDimension(finishedShape.displaySize(rendererConfig)))
        finishedRenderings += svg
      }
    })

    val svgDomElement = finishedRenderings.map(treeSvgElement => {
      val svgDim = treeSvgElement.staticBoundingBox.dimension.increaseSize(rendererConfig.paddingBig)
      val svgCanvas: SvgCanvas = new SvgCanvas(svgDim.width.toInt, svgDim.height.toInt)
      svgCanvas.addSvgElement(treeSvgElement.renderWithMods)
      div(
        draggable := true,
        onDragStart --> { mouseEvent => listener.onTreeDragged(mouseEvent, programToDisplay) },
        onDragEnd --> { mouseEvent => listener.onDragEnded(mouseEvent) },
        svgCanvas.getDomElement()
      )
    })

    val alt = div(
      cls := "altTreeDisplay",
      "no positions to draw: " + posToDraw
    )

    svgDomElement.headOption.getOrElse(alt)


    /*
    val hatchedPattern = svg.pattern(
      svg.idAttr := "hatched",
      svg.x := "0",
      svg.y := "0",
      svg.width := "20",
      svg.height := "20",
      svg.rect(
        svg.x := "0",
        svg.y := "0",
        svg.width := "10",
        svg.height := "20",
        svg.patternUnits := "userSpaceOnUse",
      )
    )

    svgCanvas.addSvgElement(hatchedPattern)*/
    /*
        tree.foreachWithStructure(structure => {
          val childNr = structure.curPosition.childIndices.last
          val bounds = boundsTree.getData(structure.curPosition).get
          val strokeWidth = curDisplayConfig.nodeSpecialStrokeWidth.getOrElse(structure.curPosition, 1)
          val svgElement = structure.curValue
            .getColorlessDisplayElement(config, bounds)
            .addModsToAll(List(
              svg.strokeWidth := strokeWidth.toString,
              svg.z := "" + structure.curPosition.level,
            ))
            .map(element => if(!element.isInstanceOf[AppTextSvgElement[_]]){
              element.addMods(List(
                svg.stroke := RGBColor.black.toWebStyleString,
                svg.fill := "transparent"
              )
              )}else{
              element.addMods(List(
                svg.stroke := RGBColor.black.toWebStyleString,
              ))
            })
            .makeClickable(mouseEvent => {
              listener.onClicked(mouseEvent, structure, displayConfigVar)
            }).makeDroppable(mouseEvent => {
              listener.onDropping(mouseEvent, structure, displayConfigVar)
            })
          val color = if(childNr % 2 == 0) "red" else "yellow"
         // svgCanvas.addSvgElement(BeShape.RectangleShape.getAssociatedSvgElement(bounds).addMods(List(svg.stroke := color, svg.fill := "transparent")).renderAsLaminar)
          svgCanvas.addSvgElement(svgElement.renderAsLaminar)
    
        }, false)
    
        div(
          draggable := true,
          onDragStart --> { mouseEvent => listener.onTreeDragged(mouseEvent, tree, displayConfigVar) },
          onDragEnd --> { mouseEvent => listener.onDragEnded(mouseEvent, tree, displayConfigVar) },
          svgCanvas.getDomElement()
        )*/

  }


}

object HtmlBeTreeDisplay {

  // Todo: Basic Shape from BeBlock, but Amends for graphic here in Display

}