package workbook.model.exercise

trait ExerciseSection {

  val exercises: List[ExerciseContent]
  val title: String

  val sectionsRequiredBefore: List[ExerciseSection]
  val sectionsRecommendedBefore: List[ExerciseSection]

}
