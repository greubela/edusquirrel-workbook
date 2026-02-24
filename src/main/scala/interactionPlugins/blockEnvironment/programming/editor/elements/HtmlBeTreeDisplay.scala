package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.Point
import contentmanagement.webElements.genericHtmlElements.canvas.SvgCanvas
import contentmanagement.webElements.svg.shapes.*
import contentmanagement.webElements.svg.shapes.composite.HorizontalAlignment.*
import contentmanagement.webElements.svg.shapes.composite.VerticalAlignment.*
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig, ControlFlowDisplay}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeTreeDropTarget, RenderingInformation}
import interactionPlugins.blockEnvironment.programming.editor.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import util.Timing

import scala.collection.mutable

case class HtmlBeTreeDisplay(
                              editorState: EditorState,
                              treeToDisplay: Signal[BeProgram],
                              treeDisplayConfig: Signal[BeTreeDisplayConfig],
                              renderingConfig: Signal[BeRenderingConfig],
                              controllerConfig: Signal[BeTreeControllerConfig],
                            ) {

  def treeRenderingSignal: Signal[(L.HtmlElement, List[BeTreeDropTarget])] = {
    treeToDisplay
      .combineWith(renderingConfig)
      .combineWith(controllerConfig)
      .combineWith(treeDisplayConfig)
      .map(tup => HtmlBeTreeDisplay.render(tup._1, tup._4, tup._2, tup._3, editorState))
  }

  def treeInContainerDiv: Element = div(
    cls := "block-tree-container",
    child <-- treeRenderingSignal.map(_._1)
  )


}

/*val nestedBlockRendererOld: NestedBlockRenderer = {
  val renderedTree = Timing.executeAndTime(
    () => tree.mapWithContext[NestedBlockRenderer](curStructure => curStructure.curValue._2.render(curStructure, renderingInfo)),
    "time to render tree old")
  renderedTree.getData(renderedTree.rootPosition.forChild(0)).get
}*/

object HtmlBeTreeDisplay {

  def forControlStructureInLibraryTab(program: BeProgram, editorState: EditorState): HtmlBeTreeDisplay = {
    val config = BeTreeDisplayConfig(
      displayPlaceholders = false,
      displayNavigation = false,
      controlFlowDisplay = ControlFlowDisplay.ControlFlowShownFull,
      compactDefinitions = true,
      compactFunctionCalls = true
    )
    HtmlBeTreeDisplay(
      editorState,
      Var(program).signal,
      Var(config).signal,
      editorState.rendererConfigVar.signal,
      editorState.libaryTreeControllerConfig.signal
    )

  }

  def forLibraryTab(program: BeProgram, editorState: EditorState): HtmlBeTreeDisplay =
    HtmlBeTreeDisplay(
      editorState,
      Var(program).signal,
      editorState.libraryTreeDisplayConfig.signal,
      editorState.rendererConfigVar.signal,
      editorState.libaryTreeControllerConfig.signal
    )

  def forSimulatedTree(treeToEdit: BeProgram, editorState: EditorState): HtmlBeTreeDisplay =
    HtmlBeTreeDisplay(
      editorState,
      Var(treeToEdit).signal,
      editorState.editorTreeDisplayConfig.signal,
      editorState.rendererConfigVar.signal,
      editorState.editTreeControllerConfig.signal
    )

  def forMainTree(editorState: EditorState): HtmlBeTreeDisplay =
    HtmlBeTreeDisplay(
      editorState,
      editorState.treeToEdit.signal,
      editorState.editorTreeDisplayConfig.signal,
      editorState.rendererConfigVar.signal,
      editorState.editTreeControllerConfig.signal
    )


  def render(programToDisplay: BeProgram, displayConfig: BeTreeDisplayConfig, rendererConfig: BeRenderingConfig, controllerConfig: BeTreeControllerConfig, editorState: EditorState): (L.HtmlElement, List[BeTreeDropTarget]) = {
    val tree: BeBlockRenderingTree = programToDisplay.blockRenderingTree(displayConfig)
    val posToDraw = tree.rootPosition.forChild(0)

    val dropTargets = mutable.ListBuffer[BeTreeDropTarget]()

    def registerDropTarget(dropTarget: BeTreeDropTarget): Any = {
      dropTargets.addOne(dropTarget)
    }

    val renderingInfo = RenderingInformation(programToDisplay, displayConfig, rendererConfig, controllerConfig, editorState, registerDropTarget)

    val nestedBlockRenderer: NestedBlockRenderer = {
      val renderedTree = Timing.executeAndTime(
        () => tree.applyWithChildResults[NestedBlockRenderer]((curStructure, childResMap) => curStructure.curValue._2.render(curStructure, childResMap, renderingInfo)),
        "time to render tree new")
      val res = renderedTree(posToDraw)
      res
    }

    val shapeToDraw = Timing.executeAndTime(() => {
      displayConfig.controlFlowDisplay match {
        case ControlFlowDisplay.ControlFlowHidden =>
          nestedBlockRenderer.expressionShapeWithoutIntendation
        case ControlFlowDisplay.ControlFlowBackgrounds | ControlFlowDisplay.ControlFlowShownFull =>
          val exprShapeNested = tree.applyWithChildResults[ControlFlowAndExpressionShape]((structure, childRes) => {
            structure.curValue._2.renderNested(structure, childRes, renderingInfo)
          })(tree.rootPosition.forChild(0))
          val shapes = displayConfig.controlFlowDisplay match {
            case ControlFlowDisplay.ControlFlowShownFull =>
              val controlFlowOverlay = nestedBlockRenderer.controlFlowOverlayShape(renderingInfo)
              List(exprShapeNested, controlFlowOverlay)
            case _ =>
              List(exprShapeNested)
          }
          ShapeStack(shapes, Left, Top)
      }
    }, "time to build shape")

    val svgDomElement = {
      val displaySize = shapeToDraw.displaySize(rendererConfig)
      val rendered = shapeToDraw.render(rendererConfig, Point[Double](0, 0).withDimension(displaySize))

      val svgCanvas: SvgCanvas = new SvgCanvas(displaySize.width.toInt, displaySize.height.toInt)
      svgCanvas.addSvgElement(rendered.renderWithMods)
      val resDiv = div(
        svgCanvas.getDomElement()
      )

      resDiv
        .amend(controllerConfig.getHtmlDragDropAmends(programToDisplay, resDiv))
        .amend(controllerConfig.getMouseAmendsForDiv(programToDisplay, resDiv))
    }

    // todo: collect extension points and store them somewhere where they can be re-used (also enable pre-view of the tree)

    (svgDomElement, dropTargets.toList)


  }

}
