package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.rendering.*

enum BeBlockDisplayStatus {
  case Normal, mouseOver, mouseOverDragging,
}

enum BeTreeDisplayStatus {
  case Normal, Dragged,
}

// def finishElement(el: AppSvgElement,
//      treeDraggedOver: Signal[Option[BeBlockTree]],
//      mouseOver: Signal[Boolean]

case class BeTreeDisplayConfig(
                                nodeSpecialStrokeWidth: Map[NodeBasedTreePosition, Double],
                                treeSpecialFill: Option[AppColor],
                              ) {

  def addHighlight(pos: NodeBasedTreePosition): BeTreeDisplayConfig = BeTreeDisplayConfig(nodeSpecialStrokeWidth + (pos -> 10.0), treeSpecialFill)

  def removeHighlight(pos: NodeBasedTreePosition): BeTreeDisplayConfig = BeTreeDisplayConfig(nodeSpecialStrokeWidth.removed(pos), treeSpecialFill)

  def resetStroke(): BeTreeDisplayConfig = BeTreeDisplayConfig(Map(), treeSpecialFill)

  def withFill(color: AppColor): BeTreeDisplayConfig = BeTreeDisplayConfig(nodeSpecialStrokeWidth, Some(color))

  def resetFill(): BeTreeDisplayConfig = BeTreeDisplayConfig(nodeSpecialStrokeWidth, None)
}

object BeTreeDisplayConfig {

  lazy val default: BeTreeDisplayConfig = BeTreeDisplayConfig(Map(), None)

}

// todo
// Display Styles: TreeAsStored // TreeExpandable // TreeWithSimulatedAddition (three different methods / classes?)
case class HtmlBeTreeDisplay(
                              treeSignal: Signal[BeBlockTree],
                              configSignal: Signal[BeRendererConfig],
                              listener: HtmlBeTreeListener
                            ) {

  val displayConfigVar: Var[BeTreeDisplayConfig] = Var(BeTreeDisplayConfig.default)

  def domSignal: Signal[L.HtmlElement] = {
    treeSignal.combineWith(configSignal).combineWith(displayConfigVar.signal).map(curTriple => {
      render(curTriple._1, curTriple._2, curTriple._3)
    })
  }

  def render(tree: Tree[NodeBasedTreePosition, BeBlock], config: BeRendererConfig, curDisplayConfig: BeTreeDisplayConfig): L.HtmlElement = {

    val boundsTree = BeProgramRendererHelper(config).toBoundsTree(tree, config.paddingBig)


    val svgWidth = boundsTree.values.map(_.endX).max + config.paddingBig.width * 2
    val svgHeight = boundsTree.values.map(_.endY).max + config.paddingBig.height * 2

    val svgCanvas: SvgCanvas = new SvgCanvas(svgWidth.toInt, svgHeight.toInt)

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
    )

  }


}

object HtmlBeTreeDisplay {

  // Todo: Basic Shape from BeBlock, but Amends for graphic here in Display

}