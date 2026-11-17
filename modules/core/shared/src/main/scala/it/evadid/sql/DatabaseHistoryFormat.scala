package it.evadid.sql

import upickle.default.*

/** Current persistence layouts; both store the same typed, timestamped events. */
sealed trait DatabaseHistoryFormat derives ReadWriter {
  def tables: List[String]
}

object DatabaseHistoryFormat {
  case object RelationalFormat extends DatabaseHistoryFormat {
    val tables = List("sync_context_id", "answer_event", "scaffolding_event", "grading_event")
  }
  case object BlobFormat extends DatabaseHistoryFormat {
    val tables = List("blob_history")
  }
}
