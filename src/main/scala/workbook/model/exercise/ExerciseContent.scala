package workbook.model.exercise

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}

trait ExerciseContent() extends SubExerciseContent {
  def titleMap: LanguageMap[HumanLanguage]
}

trait ExerciseWithText extends SubExerciseContent {
  def instructionMap: LanguageMap[HumanLanguage]
}

trait SubExerciseContent() {
  def id: String

  def estimatedTimeInMinutes: Double

}