package workbook.model.history

import workbook.model.exercise.{ExerciseContent, InteractionVariable}

import scala.collection.mutable

object ExerciseInteractionHistory {

}


trait ExerciseInteractionHistory[T]() {

  def underlyingExercise: ExerciseContent

  def editorStateVariable: InteractionVariable[T]

}
