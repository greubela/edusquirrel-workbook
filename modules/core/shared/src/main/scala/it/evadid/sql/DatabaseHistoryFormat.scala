package it.evadid.sql

import upickle.default.*

/** A storage format owns a set of tables, not necessarily a single snapshot table. */
sealed trait DatabaseHistoryFormat derives ReadWriter {
  def tables: List[String]
}

object DatabaseHistoryFormat {
  sealed trait Snapshot extends DatabaseHistoryFormat derives ReadWriter {
    def table: String
    def hasKeyTable: Boolean
    final def tables: List[String] = List(table)
  }

  case object ClassicKeylessJson extends Snapshot {
    val table = "events"
    val hasKeyTable = false
  }

  case object ClassicJsonWithKey extends Snapshot {
    val table = "eventsWithKey"
    val hasKeyTable = true
  }

  case object RelationalEvents extends DatabaseHistoryFormat {
    val tables = List("sync_context_id", "answer_event", "scaffolding_event", "grading_event")
  }
}
