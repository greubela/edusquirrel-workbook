package workbook.model.exercise

import contentmanagement.model.language.AppLanguage

case class ExerciseContent(id: String, titleMap: Map[AppLanguage, String], instructionMap: Map[AppLanguage, String]) {


}
