package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{h2, *, given}
import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeDraggingEvent, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.editor.elements.*
import interactionPlugins.blockEnvironment.programming.{BeBlockTree, BeProgram}
import org.scalajs.dom.MouseEvent
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlFullscreenTurtleEditorElement(program: Var[BeProgram]) extends HtmlWorkbookElement {


  private val initProgram = BeProgram.miniProgram()
  private val controllerVar: Var[BeControllerState] = Var(BeControllerState.defaultForTree(initProgram.logicTree))


  private val treeListener: HtmlBeTreeListener = new HtmlBeTreeListener() {

    override def onClicked(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Any = {
    }

    override def onTreeDragged(mouseEvent: MouseEvent, draggedTree: BeBlockTree): Any = {
      controllerVar.set(controllerVar.now().copy(draggingEvent = Some(BeDraggingEvent(BeProgram(draggedTree)))))
      println("Drag started: " + draggedTree)
    }

    override def onDropping(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Any = {

    }

    override def onMouseEnter(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Any = {
      print("enter(" + mouseEvent + ")")
      // displayConfig.set(displayConfig.now().addHighlight(onStructure.curPosition))
    }

    def onMouseLeave(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Any = {
      print("leave(" + mouseEvent + ")")
      //  displayConfig.set(displayConfig.now().removeHighlight(onStructure.curPosition))
    }

    def onDragEnded(mouseEvent: MouseEvent, draggedTree: BeBlockTree): Any = {
      controllerVar.set(controllerVar.now().copy(draggingEvent = None))
      println("Drag ended!")
    }

  }


  private def placeholderPanel(areaClass: String, label: String, content: Element): Element =
    div(
      cls := s"be-fullscreen-panel $areaClass",
      h2(
        cls := "be-fullscreen-panel-label",
        label
      ),
      div(
        cls := "be-fullscreen-panel-content",
        content
      )
    )

  private def placeholderPanel(areaClass: String, label: String, content: String): Element =
    div(
      cls := s"be-fullscreen-panel $areaClass",
      h2(
        cls := "be-fullscreen-panel-label",
        label
      ),
      div(
        cls := "be-fullscreen-panel-content",
        div(content)
      )
    )


  val rendererConfigSignal = Var(BeRenderingConfig.default()).signal
  val displayConfig = BeDisplayConfig.default()
  val displayConfigSignal = Var(displayConfig).signal

  def getTreeDisplay(treeSignal: Signal[BeBlockTree]): HtmlBeTreeDisplay = {

    HtmlBeTreeDisplay(treeSignal, controllerVar, displayConfigSignal, rendererConfigSignal, treeListener)

  }


  private lazy val blockLibraryDom: Element = div(
    cls := "be-fullscreen-panel block-library",
    h2(
      cls := "be-fullscreen-panel-label",
      "Block Library (Movement)"
    ),
    div(
      cls := "be-fullscreen-panel-content",
      HtmlBlockLibraryTab.turtleLibraryTab(displayConfig, controllerVar, treeListener).getDomElement()
    ),
    div(
      child <-- controllerVar.signal.map(_.draggingEvent).map(_.map("[tree with " + _.draggedTree.logicTree.size + " elements]").getOrElse("[no tree currently dragged]"))
    )
  )

  private lazy val centralWorkspaceDom: Element = div(
    cls := s"be-fullscreen-panel block-workspace",
    h2(
      cls := "be-fullscreen-panel-label",
      "Edit Program"
    ),
    div(
      cls := "be-fullscreen-panel-content",
      child <-- getTreeDisplay(controllerVar.signal.map(_.treeToEdit)).domSignal
    )
  )

  private val rootElement: Element =
    div(
      cls := "be-fullscreen-editor",

      // left
      blockLibraryDom,
      //  center
      placeholderPanel("select-function", "Select Function Area", "  "),
      centralWorkspaceDom,
      placeholderPanel("program-inspector", "Warnings and Errors", "  "),
      //  right
      placeholderPanel("output", "Nice SVG Drawing here :)", "content goes here"),
      placeholderPanel("control", "Download maybe?", "  "),

      // bottom line
      placeholderPanel("config", "Allgemeine Config (Editor, Sprache, ...)", "content goes here"),
    )

  override def getDomElement(): Element = rootElement


}
