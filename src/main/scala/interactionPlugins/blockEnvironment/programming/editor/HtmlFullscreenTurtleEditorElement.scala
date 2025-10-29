package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{h2, *, given}
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.webElements.genericHtmlElements.editor.{SimpleTextDisplay, SimpleTextEditor}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement


case class HtmlFullscreenTurtleEditorElement(initExpr: BeExpression) extends HtmlWorkbookElement {

  private val editorState: TreeEditorState = TreeEditorState.withInitExpression(initExpr)

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
      child <-- HtmlBlockLibraryTab(editorState, HtmlBlockLibraryTab.getDefaultLibraryPrograms, Var(BeTreeControllerConfig.libraryTreeConfig(editorState))).toDomSignal
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
      child <-- HtmlBeTreeDisplay(editorState.treeToEdit.signal, editorState, Var(BeTreeControllerConfig.editTreeConfig(editorState))).toDomSignal
    )
  )

  private lazy val drawingArea: Element =
    div(
      cls := s"be-fullscreen-panel output",
      h2(
        cls := "be-fullscreen-panel-label",
        "Info and Svg goes here"
      ),
      BeShape.allAtomicShapes.map(_.render(editorState.rendererConfigVar.now(), Bounds.fromPoints(Point[Double](0, 0), Point[Double](30, 30))).toPlainDisplayDiv),
      SimpleTextDisplay(editorState.treeToEdit.signal.map(curTree => Some("# Display Tree Python:\n" + curTree.asExpression.getInLanguage(Python, English)))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Mouse Over:\n" + curState.mouseOverNode.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drag Event:\n" + curState.draggingEvent.toString))).getDomElement(),
    )
    /*


     */

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
      drawingArea,
      placeholderPanel("control", "Download maybe?", "  "),

      // bottom line
      placeholderPanel("config", "Allgemeine Config (Editor, Sprache, ...)", "content goes here"),
    )

  override def getDomElement(): Element = rootElement


}
