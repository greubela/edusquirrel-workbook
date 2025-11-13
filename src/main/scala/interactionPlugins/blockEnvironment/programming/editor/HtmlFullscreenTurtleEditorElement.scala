package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{h2, *, given}
import com.raquo.laminar.api.L.{render, unsafeWindowOwner}
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
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

  val editorState: EditorState = EditorState.withInitExpression(initExpr)
  private var programBound: Boolean = false

  def bindToProgram(programVar: Var[BeProgram]): Unit = {
    if (!programBound) {
      programVar.signal.foreach { program =>
        if (editorState.treeToEdit.now() != program) {
          editorState.treeToEdit.set(program)
        }
      }(unsafeWindowOwner)

      editorState.treeToEdit.signal.foreach { program =>
        if (programVar.now() != program) {
          programVar.set(program)
        }
      }(unsafeWindowOwner)

      programBound = true
    }
  }


  private lazy val controlPanel: Element =
    HtmlEditorConfigPanel(editorState).getDomElement()

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

 

  private val testShapes: List[BeShape] = List(ControlFlowProgramStarter(), ControlFlowProgramStopper(), BeDataArrow(), ControlArrowUpDown(), IfElseSplit(), ControlFlowCross(), IfElseUnion(), ControlFlowConnectorBackground(List((true, true)))) ++ BeShape.allAtomicShapes
  private val testDims = testShapes.map(_.displaySize(editorState.rendererConfigVar.now()))

  private lazy val drawingArea: Element =
    div(
      cls := s"be-fullscreen-panel output",
      h2(
        cls := "be-fullscreen-panel-label",
        "Python is here (for debugging purposes instead of turtle graphics)"
      ),
     
      /*testShapes.zip(testDims).map((curShape, curDim) => curShape.render(editorState.rendererConfigVar.now(), Bounds(Point[Double](0, 0), curDim)).toPlainDisplayDiv),
      SimpleTextDisplay(editorState.treeToEdit.signal.map(curTree => Some("# Display Tree Python:\n" + curTree.fullProgram.getInLanguage(Python, English)))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drop Targets:\n" + curState.mouseDragOverProgram.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drag Event:\n" + curState.draggingEvent.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Mouse Over Expr:\n" + curState.mouseOverExpression.toString))).getDomElement(),*/
      )
    

  private val rootElement: Element =
    div(
      cls := "be-fullscreen-editor",

      // left
      blockLibraryDom,
      //  center
      placeholderPanel("select-function", "Select Function Area", "  "),
      HtmlBeProgramEditor(editorState).getDomElement(),
      placeholderPanel("program-inspector", "Warnings and Errors", "  "),
      //  right
      drawingArea,
      controlPanel,

      // bottom line
      placeholderPanel("config", "Allgemeine Config (Editor, Sprache, ...)", "content goes here"),
    )

  override def getDomElement(): Element = rootElement


}
