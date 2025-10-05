package interactionPlugins.gpt

import contentmanagement.model.language.AppLanguage
import interactionPlugins.blockEnvironment.firstIteration.TurtleProgramState
import workbook.model.exercise.ExerciseContent

case class TextBasedGptExercise(
                                 id: String,
                                 titleMap: Map[AppLanguage, String],
                                 instructionMap: Map[AppLanguage, String]
                               ) extends ExerciseContent{

  override def estimatedTimeInMinutes: Double = 2


}
