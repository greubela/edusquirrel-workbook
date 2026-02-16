package workbook.model.exercise

import contentmanagement.model.language.AppLanguage

trait ExerciseContent() extends SubExerciseContent {
  def titleMap: Map[AppLanguage, String]
}

trait SubExerciseContent() {
  def id: String

  def estimatedTimeInMinutes: Double

  def instructionMap: Map[AppLanguage, String]
}