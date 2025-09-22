package workbook.workbookHtmlElements.interactions

import com.raquo.laminar.api.L.*
import contentmanagement.htmlElements.genericElements.editor.SimpleTextEditor
import feedback.gpt.{GptGrader, GptScaffolder}
import workbook.model.states.BasicVariableBasedState.BasicStringState
import workbook.model.feedback.grading.BasicStringGradingResult
import workbook.model.feedback.scaffolding.BasicStringScaffoldingResult
import workbook.model.interaction.full.{FullInteraction, FullInteractionController, FullInteractionModel, FullInteractionVisualizer}
import workbook.model.states.{BasicVariableBasedState, FullInteractionState, Stateless}
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionContainer

case class TextBasedGptInteraction(initEditorText: String) extends FullInteraction[
  BasicStringState, BasicStringState, Stateless,
  BasicStringScaffoldingResult, BasicStringGradingResult,
  SimpleTextEditor, GptScaffolder, GptGrader
] {

  private val initStateEditor = BasicVariableBasedState.createStringState("[editor text goes here]")
  private val initStateScaffolder = BasicVariableBasedState.createStringState("[scaffolder text goes here]")
  private val initState = FullInteractionState[BasicStringState, BasicStringState, Stateless](initStateEditor, initStateScaffolder, Stateless.StatelessInstance)


  val model = new FullInteractionModel[BasicStringState, BasicStringState, Stateless, BasicStringScaffoldingResult, BasicStringGradingResult](initState)


  private val inputEditor = {
    val res = SimpleTextEditor(initStateEditor)
    res.addObserver(newTextareaText => {
      println("observer observed input editor change: " + newTextareaText)
      val newEditorState = model.currentState.copy(editorState = BasicVariableBasedState.createStringState(newTextareaText))
      model.logNewInteractionState(newEditorState)
    })
    res
  }

  private val scaffoldingEditor = {
    val res = SimpleTextEditor(initStateScaffolder)
    res.addObserver(newTextareaText => {
      println("observer observed scaffolding change: " + newTextareaText)
      val newScaffoldingState = model.currentState.copy(scaffoldingState = BasicVariableBasedState.createStringState(newTextareaText))
      model.logNewInteractionState(newScaffoldingState)
    })
    res
  }

  val controller = FullInteractionController(inputEditor, GptScaffolder(), GptGrader())


  private def showTextInDisabledTextBox(classStr: String, text: String): Element = div(
    cls := classStr,
    textArea(
      text,
      disabled := true,
      rows := 8,
      cols := 80,
    )
  )

  val visualizer: FullInteractionVisualizer[BasicStringState, BasicStringState, Stateless, BasicStringScaffoldingResult, BasicStringGradingResult, SimpleTextEditor, GptScaffolder, GptGrader] = new FullInteractionVisualizer[BasicStringState, BasicStringState, Stateless, BasicStringScaffoldingResult, BasicStringGradingResult, SimpleTextEditor, GptScaffolder, GptGrader]() {
    override def visualizeEditor(curState: BasicStringState): Element = inputEditor.getDomElement()

    def visualizeScaffolderStateEditor(curState: BasicStringState): Element = scaffoldingEditor.getDomElement()

    def visualizeScaffoldingResult(curScaffolderState: BasicStringState, curResult: BasicStringScaffoldingResult): Element = showTextInDisabledTextBox("cls-scaffolder", curResult.toString())

    def visualizeGraderStateEditor(curState: Stateless): Element = div()

    def visualizeGradingResult(curGradingState: Stateless, curResult: BasicStringGradingResult): Element = showTextInDisabledTextBox("cls-grading", curResult.toString())

  }



}
