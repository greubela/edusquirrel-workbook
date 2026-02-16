package workbook.model.history

import workbook.model.exercise.{ExerciseContent, ExerciseVariable}

import scala.collection.mutable

object ExerciseInteractionHistory {

}


trait ExerciseInteractionHistory[T]() {

  def underlyingExercise: ExerciseContent

  def editorStateVariable: ExerciseVariable[T]

}
