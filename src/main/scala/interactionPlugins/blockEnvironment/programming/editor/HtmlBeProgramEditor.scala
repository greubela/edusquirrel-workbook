package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.color.RGBColor
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.call.*
import interactionPlugins.blockEnvironment.programming.blocks.call.parent.BeBlockParent
import interactionPlugins.blockEnvironment.programming.rendering.*

import scala.collection.mutable

case class HtmlBeProgramEditor(programVar: Signal[BeProgram], config: BeRendererConfig) {


  // Observable Variables: Tree + Current Position that receives input (if any -> option)
  // Automatically derive Tree with NON Finished Elements?

  // todo
  // def dragFrom -> Tree // DraggedFromPosition
  // def dropInto(Tree, fromPosition)
  // todo
  // layout manager not in component, but separate (sealed traits?)
  // simulate after drop, transparent
  // todo
  // Display Styles: TreeAsStored // TreeExpandable // TreeWithSimulatedAddition (three different methods / classes?)

  val svgCanvasSignal: Signal[SvgCanvas] = programVar.map(program => render(program.displayTree.map(_.asInstanceOf[BeBlock])))


  def render(tree: Tree[NodeBasedTreePosition, BeBlock]): SvgCanvas = {

    val boundsTree = BeProgramRendererHelper(config).toBoundsTree(tree, config.paddingBig)

    val svgWidth = boundsTree.values.map(_.endX).max + config.paddingBig.width * 2
    val svgHeight = boundsTree.values.map(_.endY).max + config.paddingBig.height * 2

    val svgCanvas: SvgCanvas = new SvgCanvas(svgWidth.toInt, svgHeight.toInt)

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

    svgCanvas.addSvgElement(hatchedPattern)

    tree.foreach((curPos, curBlock) => {
      val bounds = boundsTree.getData(curPos).get

      val shape = curBlock.layoutManager.shapeFactory(bounds)
      val fillString = curBlock.layoutManager.fill(config)
      val strokeString = curBlock.layoutManager.stroke(config)

      val mods: List[Modifier[L.SvgElement]] = List(
        svg.stroke := RGBColor.black.toWebStyleString,
        svg.fill := fillString,
        svg.stroke := strokeString,
        svg.strokeWidth := "2",
        svg.z := "" + curPos.level,
      )

      val laminarElement = shape.renderWithController(mods, event => println("clicked on: " + curBlock), event => println("dragged: " + curBlock), event => println("dropped: " + curBlock))

      svgCanvas.addSvgElement(laminarElement)

    }, false)

    svgCanvas

  }


}
