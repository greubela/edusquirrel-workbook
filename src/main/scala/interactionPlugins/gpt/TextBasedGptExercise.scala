package interactionPlugins.gpt

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.exercise.{ExerciseContent, ExerciseWithText, SubExerciseContent}

case class TextBasedGptExercise(
                                 id: String,
                                 titleMap: LanguageMap[HumanLanguage],
                                 instructionMap: LanguageMap[HumanLanguage]
                               ) extends ExerciseContent with ExerciseWithText {
  

    override def estimatedTimeInMinutes: Double = 2
  }
