package interactionPlugins.pythonExercises

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise
import workbook.model.feedback.scaffolding.BasicVariableScaffoldingResult
import workbook.model.states.Stateless

final class HtmlPythonExercise(override val exerciseContent: PythonExerciseContent) extends HtmlFullInteractionExercise[
  PythonEditorState,
  Stateless,
  PythonGradingState,
  BasicVariableScaffoldingResult[String, Stateless],
  PythonGradingResult,
  PythonScaffolder,
  PythonGrader
] {

  override val htmlInteractionModel: HtmlPythonInteractionModel = new HtmlPythonInteractionModel(exerciseContent)

  override val htmlTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)

  override val htmlInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  override val htmlInteractionContainer: HtmlPythonInteractionContainer =
    new HtmlPythonInteractionContainer(this, htmlInteractionModel)

  private val domElement =
    div(
      cls := "container-exercise python-exercise style-vbox",
      htmlTitleElement.getDomElement(),
      htmlInstructionElement.getDomElement(),
      htmlInteractionContainer.getDomElement()
    )

  override def getDomElement(): L.Element = domElement
}
