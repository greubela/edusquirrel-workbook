package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.genericHtmlElements.editor.{SimpleStringTextEditor, SimpleTextDisplay, SimpleTextEditor}
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.*
import workbook.model.display.InteractionComponent.InteractionContentRole.*
import workbook.model.feedback.grading.GptGradingResult
import workbook.model.feedback.scaffolding.GptScaffoldingResult
import workbook.model.interaction.full.*
import workbook.model.states.BasicVariableBasedState.BasicStringState
import workbook.model.states.{BasicVariableBasedState, Stateless}

case class HtmlTextBasedGptInteractionModel(initEditorText: String, initScaffolderText: String) extends HtmlFullInteractionModel[
  BasicStringState, BasicStringState, Stateless,
  GptScaffoldingResult, GptGradingResult,
  GptScaffolder, GptGrader
] {

  private val initStateEditor = BasicVariableBasedState.createStringState(initEditorText)
  private val initStateScaffolder = BasicVariableBasedState.createStringState(initScaffolderText)

  val model = new FullInteractionExerciseModel[BasicStringState, BasicStringState, Stateless, GptScaffoldingResult, GptGradingResult](initStateEditor, initStateScaffolder, Stateless.StatelessInstance)

  val controller = FullInteractionController(GptScaffolder(), GptGrader())

  val visualizer: FullInteractionVisualizer[BasicStringState, BasicStringState, Stateless, GptScaffoldingResult, GptGradingResult, GptScaffolder, GptGrader]
  = new FullInteractionVisualizer[BasicStringState, BasicStringState, Stateless, GptScaffoldingResult, GptGradingResult, GptScaffolder, GptGrader] {

    override def visualizeEditor(data: Var[BasicStringState]): InteractionComponentForRole = {
      val editor: InteractionComponent = SimpleTextEditor(data)
      InteractionWithRole(editor, Editor)
    }

    override def visualizeScaffolderStateEditor(data: Var[BasicStringState]): InteractionComponentForRole = {
      val editor: InteractionComponent = SimpleTextEditor(data)
      InteractionWithRole(editor, ScaffoldingStateEditor)
    }

    override def visualizeGraderStateEditor(data: Var[Stateless]): InteractionComponentForRole = new InteractionComponentForRole {
      override def forContentRole: InteractionContentRole = GradingStateEditor

      override def getDomElement(): L.Element = div("nothing to see here :D")

      override def setHighlight(highlight: Boolean): Unit = {}

      override def setVisible(visible: Boolean): Unit = {}

      override def setDisabled(disabled: Boolean): Unit = {}
    }

    override def visualizeScaffoldingResult(data: Var[Option[GptScaffoldingResult]]): InteractionComponentForRole = {
      val editor: InteractionComponent = SimpleTextDisplay(data.signal.map(_.map(_.variable)))
      InteractionWithRole(editor, ScaffoldingResult)
    }

    override def visualizeGradingResult(data: Var[Option[GptGradingResult]]): InteractionComponentForRole = {
      val editor: InteractionComponent = SimpleTextDisplay(data.signal.map(_.map(_.variable)))
      InteractionWithRole(editor, GradingResult)
    }
  }

}
