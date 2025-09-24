package interactionPlugins.turtleEnvironment

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

class HtmlTurtleExercise(override val exerciseContent: TurtleExerciseContent) extends HtmlFullInteractionExercise[
  TurtleEditorState,
  TurtleScaffoldingState,
  TurtleGradingState,
  TurtleScaffoldingFeedback,
  TurtleGradingFeedback,
  TurtleScaffolder,
  TurtleGrader
] {

  override val htmlInteractionModel: HtmlTurtleInteractionModel = new HtmlTurtleInteractionModel(exerciseContent)

  override val htmlTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)

  override val htmlInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  override val htmlInteractionContainer: HtmlTurtleInteractionContainer =
    new HtmlTurtleInteractionContainer(this, htmlInteractionModel)

  private val domElement =
    div(
      cls := "container-exercise turtle-environment style-vbox",
      htmlTitleElement.getDomElement(),
      htmlInteractionContainer.getDomElement()
    )

  override def getDomElement(): L.Element = domElement
}
