package interactionPlugins.automaton

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

class HtmlAutomatonExercise(override val exerciseContent: AutomatonExerciseContent)
    extends HtmlFullInteractionExercise[
      AutomatonEditorState,
      AutomatonScaffoldingState,
      AutomatonGradingState,
      AutomatonScaffoldingFeedback,
      AutomatonGradingFeedback,
      AutomatonScaffolder,
      AutomatonGrader
    ] {

  override val htmlInteractionModel: HtmlAutomatonInteractionModel = new HtmlAutomatonInteractionModel(exerciseContent)

  override val htmlTitleElement: HtmlExerciseTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)

  override val htmlInstructionElement: HtmlPlaintextInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  override val htmlInteractionContainer: HtmlAutomatonInteractionContainer =
    HtmlAutomatonInteractionContainer(this, htmlInteractionModel)

  private val domElement =
    div(
      cls := "container-exercise style-vbox automaton-environment",
      htmlTitleElement.getDomElement(),
      htmlInteractionContainer.getDomElement()
    )

  override def getDomElement(): L.Element = domElement
}
