package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import interactionPlugins.gpt.HtmlTextBasedGptInteractionModel
import workbook.model.exercise.ExerciseContent
import workbook.workbookHtmlElements.container.HtmlFullInteractionContainerDefault
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

case class ProgrammingExercise(
                                    id: String,
                                    titleMap: Map[AppLanguage, String],
                                    instructionMap: Map[AppLanguage, String]
                                  ) extends ExerciseContent {


  override def estimatedTimeInMinutes: Double = 3
}
