package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{h2, *, given}
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.AppLanguage.{English, German, Python}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.parsing.python.PythonParser
import contentmanagement.webElements.genericHtmlElements.editor.{SimpleStringTextEditor, SimpleTextDisplay}
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.controlflow.*
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.{ControlFlowCross, IfElseSplit, IfElseUnion}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowProgramStarter, ControlFlowProgramStopper}
import contentmanagement.webElements.svg.shapes.decorations.{BeDataArrow, ControlArrowUpDown}
import interactionPlugins.blockEnvironment.config.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.*
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import contentmanagement.webElements.genericHtmlElements.editor.*

case class HtmlFullscreenTurtleEditorElement(initExpr: BeExpression) extends HtmlWorkbookElement {

  private val editorState: EditorState = EditorState.withInitExpression(initExpr)
  val strVar: Var[String] = Var(editorState.treeToEdit.now().fullProgram.getInLanguage(Python, English))
  editorState.treeToEdit.signal.foreach(tree => strVar.update(_ => tree.fullProgram.getInLanguage(Python, English) ))(new Owner(){})
  var language: AppLanguage = AppLanguage.English


  //HtmlBeTreeDisplay(editorState.treeToEdit.signal, editorState, Var(BeTreeControllerConfig.editTreeConfig(editorState)), _.editorTreeDisplayConfig).toDomSignal
  //private val centralTreeSignal: Signal[()]


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
      child <-- editorState.treeToDisplaySignal
    )
  )

  private val testShapes: List[BeShape] = List(ControlFlowProgramStarter(), ControlFlowProgramStopper(), BeDataArrow(), ControlArrowUpDown(), IfElseSplit(), ControlFlowCross(), IfElseUnion(), ControlFlowConnectorBackground(List((true, true)))) ++ BeShape.allAtomicShapes
  private val testDims = testShapes.map(_.displaySize(editorState.rendererConfigVar.now()))

  private lazy val drawingArea: Element =
    div(
      cls := s"be-fullscreen-panel output",
      h2(
        cls := "be-fullscreen-panel-label",
        "Python is here (for debugging purposes instead of turtle graphics)"
      ),
      SimpleStringTextEditor(strVar).getDomElement(),
      /*testShapes.zip(testDims).map((curShape, curDim) => curShape.render(editorState.rendererConfigVar.now(), Bounds(Point[Double](0, 0), curDim)).toPlainDisplayDiv),
      SimpleTextDisplay(editorState.treeToEdit.signal.map(curTree => Some("# Display Tree Python:\n" + curTree.fullProgram.getInLanguage(Python, English)))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drop Targets:\n" + curState.mouseDragOverProgram.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drag Event:\n" + curState.draggingEvent.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Mouse Over Expr:\n" + curState.mouseOverExpression.toString))).getDomElement(),*/
      button(
        "Sync: Python -> Blöcke!",
        onClick --> { _ =>
          editorState.treeToEdit.update(oldTree => {
            BeProgram(BeStartProgram(PythonParser.parsePython(strVar.now())))
          })
        } // event handler
      ),
      button(
        "Language: EN",
        onClick --> { _ =>
          strVar.update(oldStr => {
            editorState.treeToEdit.now().fullProgram.getInLanguage(Python, English)
          })
        } // event handler
      ),
      button(
        "Language: DE",
        onClick --> { _ =>
          strVar.update(oldStr => {
            editorState.treeToEdit.now().fullProgram.getInLanguage(Python, German)
          })
        } // event handler
      ),


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
