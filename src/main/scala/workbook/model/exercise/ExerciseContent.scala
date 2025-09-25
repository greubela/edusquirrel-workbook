package workbook.model.exercise

import contentmanagement.model.language.AppLanguage

trait ExerciseContent() {
  def id: String

  def titleMap: Map[AppLanguage, String]

  def estimatedTimeInMinutes: Double

  def instructionMap: Map[AppLanguage, String]
}
