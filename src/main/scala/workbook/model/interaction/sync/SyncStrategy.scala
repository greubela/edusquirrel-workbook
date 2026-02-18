package workbook.model.interaction.sync

import workbook.model.interaction.sync.*
import workbook.model.interaction.history.*
import workbook.model.interaction.*


sealed trait SyncStrategy {

  def selectEventsToSync[T](events: List[InteractionVariableState[T]]): List[InteractionVariableState[T]]

}

object SyncStrategy {

  object SYNC_EVERYTHING extends SyncStrategy{
    override def selectEventsToSync[T](events: List[InteractionVariableState[T]]): List[InteractionVariableState[T]] = events
  }
  //, SYNC_MAJOR_ONLY, SYNC_LAST_ONLY

}

