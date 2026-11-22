package it.evadid.workbook.interaction.sync

import scala.concurrent.Future

/** Runtime reset state shared by the sync control and its UI; never persisted. */
enum SyncResetState {
  case Idle
  case Running(completion: Future[Unit])
  case Failed(cause: Throwable)
  case Completed
}
