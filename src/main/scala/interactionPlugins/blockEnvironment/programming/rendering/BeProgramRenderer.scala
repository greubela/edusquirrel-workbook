package interactionPlugins.blockEnvironment.programming.rendering

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.color.RGBColor
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import interactionPlugins.blockEnvironment.programming.*

import scala.collection.mutable

case class BeProgramRenderer(program: BeProgram, config: BeRendererConfig) {

  lazy val tree: BeProgramTree = program.tree

  lazy val minSizeTree: BeDimensionTree = {
    program.applyMapFunc[Dimension[Double]]((block, context) => block.layoutManager.getMinSize(config, context))
  }

  lazy val displaySizeTree: BeDimensionTree = minSizeTree

  lazy val relativeOffsets: BePositionTree = {
    val offsetMap = mutable.HashMap[NodeBasedTreePosition, Point[Double]]()

    def handleChildren(layoutManager: BeBlockLayoutManager, children: List[(BeBlock, NodeBasedTreePosition)]): Unit = {
      val before = mutable.ListBuffer[(BeBlock, Point[Double], Dimension[Double])]()
      for (curChild <- children) {
        val offset = layoutManager.calculateRelativeChildOffset(config, before.toList, curChild._1)
        before.append((curChild._1, offset, displaySizeTree.getData(curChild._2).get))
        offsetMap.put(curChild._2, offset)
      }
    }

    def calcChildrenList(curPosition: NodeBasedTreePosition): List[(BeBlock, NodeBasedTreePosition)] = {
      val children = tree.getChildren(curPosition)
      val res = children.map(curChildPosition => {
        val childBlockAtPos: Option[BeBlock] = tree.getData(curChildPosition)
        (childBlockAtPos.get, curChildPosition)
      })
      res.toList
    }

    val rootLayoutManager = BeBlockLayoutManager.SimpleVBoxChildrenLayoutManager(Point[Double](0, 0), Dimension[Double](100, 100), None)
    handleChildren(rootLayoutManager, calcChildrenList(tree.rootPosition))

    tree.foreach((curPosition, curBlock) => handleChildren(curBlock.layoutManager, calcChildrenList(curPosition)))

    program.applyMapFunc((block, context) => offsetMap(context.curTraversalInformation.curPosition))
  }

  lazy val absoluteOffsets: BePositionTree = {
    relativeOffsets.mapWithContext((curBlock, context) => if (context.curTraversalInformation.curPosition.level == 1) {
      relativeOffsets.getData(context.curTraversalInformation.curPosition).get
    } else {
      context.accessParentResult.get + relativeOffsets.getData(context.curTraversalInformation.curPosition).get
    })
  }

  lazy val boundsTree: BeBoundsTree = {
    tree.mapWithContext((curBlock, curContext) => {
      val absoluteOffset = absoluteOffsets.getData(curContext.curTraversalInformation.curPosition).get
      val dimension = displaySizeTree.getData(curContext.curTraversalInformation.curPosition).get
      absoluteOffset.withDimension(dimension)
    })
  }


  def render(): SvgCanvas = {

    boundsTree.foreach((curPos, curBounds) => {println("BoundsTree\t\t" + curPos.toString + " " + curBounds.toString )}, false)

    val svgWidth = boundsTree.values.map(_.endX).max
    val svgHeight = boundsTree.values.map(_.endY).max

    val svgCanvas: SvgCanvas = new SvgCanvas(svgWidth.toInt, svgHeight.toInt)

    tree.foreach((curPos, curBlock) => {
      val bounds = absoluteOffsets.getData(curPos).get.withDimension(displaySizeTree.getData(curPos).get)

      val shape = curBlock.layoutManager.useDifferentShape.getOrElse(curBlock.evaluatesTo.shapeFactory).apply(bounds)

      val mods: List[Modifier[L.SvgElement]] = List(
        svg.stroke := RGBColor.black.toWebStyleString,
        svg.fill := curBlock.evaluatesTo.color.toWebStyleString,
        svg.z := "" + curPos.level
      )
      svgCanvas.addSvgElement(shape.renderAsLaminar(mods))

    }, false)

    svgCanvas

  }


}
