package interactionPlugins.automaton

import contentmanagement.model.language.AppLanguage
import workbook.model.exercise.ExerciseContent

case class AutomatonExerciseContent(
  id: String,
  titleMap: Map[AppLanguage, String],
  instructionMap: Map[AppLanguage, String],
  shouldAccept: List[String],
  shouldReject: List[String],
  defaultMode: AutomatonMode = AutomatonMode.Dfa
) extends ExerciseContent {
  

  override def estimatedTimeInMinutes: Double = 7.5

}

object AutomatonExerciseContent {

  def apply(
    id: String,
    titleMap: Map[AppLanguage, String],
    instructionMap: Map[AppLanguage, String],
    shouldAccept: List[String],
    shouldReject: List[String],
    defaultMode: AutomatonMode = AutomatonMode.Dfa
  ): AutomatonExerciseContent =
    new AutomatonExerciseContent(id, titleMap, instructionMap, shouldAccept, shouldReject, defaultMode)


  val divisibleByThree: AutomatonExerciseContent = AutomatonExerciseContent(
    id = "automaton-001",
    titleMap = Map(AppLanguage.English -> "Finite automaton: length divisible by three"),
    instructionMap = Map(AppLanguage.English -> "Build an automaton that accepts exactly the strings whose length is divisible by three."),
    shouldAccept = List("", "000", "111", "010", "101010", "ababab"),
    shouldReject = List("0", "00", "01", "10", "101", "ababa"),
    defaultMode = AutomatonMode.Dfa
  )
}
