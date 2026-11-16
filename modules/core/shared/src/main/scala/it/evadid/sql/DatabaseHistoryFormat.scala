package it.evadid.sql

import upickle.default.*

/** Event-based relational history storage. */
sealed trait DatabaseHistoryFormat derives ReadWriter {
  def tables: List[String]
}

object DatabaseHistoryFormat {
  case object RelationalEvents extends DatabaseHistoryFormat {
    val tables = List("sync_context_id", "answer_event", "scaffolding_event", "grading_event")
  }
}
