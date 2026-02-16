package workbook.model.history.sync

import workbook.model.exercise.ExerciseVariable.ExerciseVariableState


sealed trait SyncStrategy {

  def selectEventsToSync[T](events: List[ExerciseVariableState[T]]): List[ExerciseVariableState[T]]

}

object SyncStrategy {

  object SYNC_EVERYTHING extends SyncStrategy{
    override def selectEventsToSync[T](events: List[ExerciseVariableState[T]]): List[ExerciseVariableState[T]] = events
  }
  //, SYNC_MAJOR_ONLY, SYNC_LAST_ONLY

}

