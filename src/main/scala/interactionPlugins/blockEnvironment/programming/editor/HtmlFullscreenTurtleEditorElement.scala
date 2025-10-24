package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.laminar.api.L.{h2, *, given}
import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.editor.elements.{BeTreeDisplayConfig, HtmlBeTreeListener, HtmlBlockLibraryTab}
import interactionPlugins.blockEnvironment.programming.{BeBlockTree, BeProgram}
import org.scalajs.dom.MouseEvent
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlFullscreenTurtleEditorElement(program: Var[BeProgram]) extends HtmlWorkbookElement {


  private val draggedTreeVar: Var[Option[BeBlockTree]] = Var(None)
  private val draggedTreeDisplayConfigVar: Var[Option[Var[BeTreeDisplayConfig]]] = Var(None)

  private val libraryTreeListener: HtmlBeTreeListener = new HtmlBeTreeListener() {

    override def onClicked(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock], displayConfig: Var[BeTreeDisplayConfig]): Any = {
    }

    override def onTreeDragged(mouseEvent: MouseEvent, draggedTree: BeBlockTree, displayConfig: Var[BeTreeDisplayConfig]): Any = {
      draggedTreeVar.set(Some(draggedTree))
      draggedTreeDisplayConfigVar.set(Some(displayConfig))
      //  displayConfig.set(displayConfig.now().withFill(RGBColor.yellow))
    }

    override def onDropping(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock], displayConfig: Var[BeTreeDisplayConfig]): Any = {
      draggedTreeVar.set(None)
     /* val oldConfig = draggedTreeDisplayConfigVar.now()
      if (oldConfig.nonEmpty) {
        oldConfig.get.set(oldConfig.get.now().resetFill())
      }*/
      draggedTreeDisplayConfigVar.set(None)
    }

    override def onMouseEnter(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock], displayConfig: Var[BeTreeDisplayConfig]): Any = {
      print("enter(" + mouseEvent + ")")
      println("enter(" + mouseEvent.clientX + ", " + mouseEvent.clientY + "): " + onStructure.curPosition.toString + ", config: " + displayConfig.now().toString + "!")
      // displayConfig.set(displayConfig.now().addHighlight(onStructure.curPosition))
    }

    def onMouseLeave(mouseEvent: MouseEvent, onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock], displayConfig: Var[BeTreeDisplayConfig]): Any = {
      print("leave(" + mouseEvent + ")")
      println("leave(" + mouseEvent.clientX + ", " + mouseEvent.clientY + "): " + onStructure.curPosition.toString + ", config: " + displayConfig.now().toString + "!")
      //  displayConfig.set(displayConfig.now().removeHighlight(onStructure.curPosition))
    }

    def onDragEnded(mouseEvent: MouseEvent, draggedTree: BeBlockTree, displayConfig: Var[BeTreeDisplayConfig]): Any = {
      draggedTreeVar.set(None)
      draggedTreeDisplayConfigVar.set(None)
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


  private lazy val blockLibraryDom: Element = div(
    cls := "be-fullscreen-panel block-library",
    h2(
      cls := "be-fullscreen-panel-label",
      "Block Library (Movement)"
    ),
    div(
      cls := "be-fullscreen-panel-content",
      HtmlBlockLibraryTab.turtleLibraryTab(libraryTreeListener).getDomElement()
    ),
    div(
      child <-- draggedTreeVar.signal.map(_.map("[tree with " + _.size + " elements]").getOrElse("[no tree]"))
    )
  )

  private val rootElement: Element =
    div(
      cls := "be-fullscreen-editor",

      // left
      blockLibraryDom,
      //  center
      placeholderPanel("select-function", "Select Function Area", "content goes here"),
      placeholderPanel("block-workspace", "Display and Edit Functions", "content goes here"),
      placeholderPanel("program-inspector", "Warnings and Errors", "content goes here"),
      //  right
      placeholderPanel("output", "Nice SVG Drawing here :)", "content goes here"),
      placeholderPanel("control", "Download maybe?", "content goes here"),

      // bottom line
      placeholderPanel("config", "Allgemeine Config (Editor, Sprache, ...)", "content goes here"),
    )

  override def getDomElement(): Element = rootElement


}
