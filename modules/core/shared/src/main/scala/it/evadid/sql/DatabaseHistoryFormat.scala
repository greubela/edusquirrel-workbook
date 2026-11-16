package it.evadid.sql

import upickle.default.*

/** Database history storage formats.
  *
  * - `RelationalEvents`: Stores history in separate relational tables (sync_context_id, answer_event, grading_event, scaffolding_event)
  * - `SingleBlob`: Stores entire InteractionVariableHistory as JSON in a single column, merging on update
  */
sealed trait DatabaseHistoryFormat derives ReadWriter {
  def tables: List[String]
}

object DatabaseHistoryFormat {
  case object RelationalEvents extends DatabaseHistoryFormat {
    val tables = List("sync_context_id", "answer_event", "scaffolding_event", "grading_event")
  }

  /** Single-blob format: entire history stored as JSON, merged on update.
    *
    * This format stores the complete InteractionVariableHistory as a single JSON string
    * in one column per sync context. When storing new elements, the existing history
    * is read first, merged with the new data, and written back.
    *
    * This is similar to the old ClassicWithKey approach but with merge semantics.
    */
  case object SingleBlob extends DatabaseHistoryFormat {
    val tables = List("single_blob_history")
  }
}
