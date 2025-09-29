package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.genericHtmlElements.editor.SimpleTextEditor
import interactionPlugins.gpt.HtmlTextBasedGptInteractionModel
import interactionPlugins.blockProgramming.TurtleExerciseContent
import workbook.model.display.FullInteractionLabelModel
import workbook.model.exercise.ExerciseContent
import workbook.model.feedback.grading.GptGradingResult
import workbook.model.feedback.scaffolding.GptScaffoldingResult
import workbook.model.interaction.full.HtmlFullInteractionModel
import workbook.model.states.BasicVariableBasedState.BasicStringState
import workbook.model.states.Stateless
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise
import workbook.workbookHtmlElements.container.HtmlFullInteractionContainerDefault
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

case class HtmlTextBasedGptExercise(exerciseContent: TextBasedGptExercise) extends HtmlFullInteractionExercise[
  BasicStringState, BasicStringState, Stateless,
  GptScaffoldingResult, GptGradingResult,
  GptScaffolder, GptGrader
] {

  override val htmlInteractionModel: HtmlFullInteractionModel[BasicStringState, BasicStringState, Stateless, GptScaffoldingResult, GptGradingResult, GptScaffolder, GptGrader] =
    HtmlTextBasedGptInteractionModel("[write your solutions into this text box]", "[if you are stuck, ask a question with this text box]")

  override val htmlTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)
  override val htmlInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)
  override val htmlInteractionContainer = HtmlFullInteractionContainerDefault(this, htmlInteractionModel, FullInteractionLabelModel.defaultInteractionLabeling)

  private val domElement: Element = div(cls := "container-exercise style-vbox",
    List(
      htmlTitleElement.getDomElement(),
      htmlInstructionElement.getDomElement(),
      htmlInteractionContainer.getDomElement())
  )

  override def getDomElement(): L.Element = domElement

}
