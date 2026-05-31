package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{h2, *}
import it.evadid.homepage.HtmlAppElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBeProgramEditor, HtmlBlockLibraryTab, HtmlEditorConfigPanel}
import todomove.datastructures.core.vm.code.BeExpression

object HtmlFullscreenTurtleEditorElement {

  def apply(initExpr: BeExpression): HtmlFullscreenTurtleEditorElement = {
    new Exception("[WARN] created Turtle Editor without useful variable to be bound to!").printStackTrace()
    HtmlFullscreenTurtleEditorElement(EditorState.withInitExpression(initExpr))
  }

}

case class HtmlFullscreenTurtleEditorElement(editorState: EditorState) extends HtmlAppElement {

  //private val editorState: EditorState = EditorState.forWorkbookAndInteractionVariable(forVar)
  private var programBound: Boolean = false

  /*
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
  }*/


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
    HtmlBlockLibraryTab.getDefaultTurtleLibraryTab(editorState).getDomElement()
  )

 

//  private val testShapes: List[BeShape] = List(ControlFlowProgramStarter(), ControlFlowProgramStopper(), BeDataArrow(), ControlArrowUpDown(), IfElseSplit(), ControlFlowCross(), IfElseUnion(), ControlFlowConnectorBackground(List((true, true)))) ++ BeShape.allAtomicShapes
//  private val testDims = testShapes.map(_.displaySize(editorState.rendererConfigVar.now()))

  private val showExpectedOutputVar: Var[Boolean] = Var(false)

  private lazy val drawingArea: Element =
    div(
      cls := s"be-fullscreen-panel output",
      h2(
        cls := "be-fullscreen-panel-label",
        //"Python is here (for debugging purposes instead of turtle graphics)"
        "Turtle Graphics"
      ),
       /*testShapes.zip(testDims).map((curShape, curDim) => curShape.render(editorState.rendererConfigVar.now(), Bounds(Point[Double](0, 0), curDim)).toPlainDisplayDiv),
      SimpleTextDisplay(editorState.treeToEdit.signal.map(curTree => Some("# Display Tree Python:\n" + curTree.fullProgram.getInLanguage(Python, English)))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drop Targets:\n" + curState.mouseDragOverProgram.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Drag Event:\n" + curState.draggingEvent.toString))).getDomElement(),
      SimpleTextDisplay(editorState.controllerStateVar.signal.map(curState => Some("Cur Mouse Over Expr:\n" + curState.mouseOverExpression.toString))).getDomElement(),*/
      div(
        cls := "be-fullscreen-panel-content turtle-graphics-container",
        div(
          cls := "turtle-graphics-section",
          h3("Turtle Output"),
          img(
            src := "../resources/img/turtle_output.png",
            alt := "Actual turtle output",
            styleAttr := "width: 100%;"
          ),
          button(
            cls := "turtle-toggle-button",
            onClick --> { _ => showExpectedOutputVar.update(!_) },
            child <-- showExpectedOutputVar.signal.map(if (_) "Hide Expected Output" else "Show Expected Output")
          )
        ),
        child <-- showExpectedOutputVar.signal.map(showExpected =>
          if (showExpected)
            div(
              cls := "turtle-graphics-section",
              h3("Expected Turtle Output"),
              img(
                src := "../resources/img/expected_turtle_output.png",
                styleAttr := "width: 100%;"
                /*alt := "Expected turtle output"*/
              )
            )
          else
            emptyNode
        )
      )
    )
    

  private val rootElement: Element = {
    div(
      cls := "be-fullscreen-editor",

      // left
      blockLibraryDom,
      //  center
      placeholderPanel("select-function", "Select Function Area", "  "),
      HtmlBeProgramEditor(editorState).getDomElement(),
      // placeholderPanel("program-inspector", "Warnings and Errors", "  "),
      //  right
      drawingArea,
      controlPanel,

      // bottom line
      placeholderPanel("config", "Allgemeine Config (Editor, Sprache, ...)", "content goes here"),
    )
  }

  override def getDomElement(): Element = rootElement


}
