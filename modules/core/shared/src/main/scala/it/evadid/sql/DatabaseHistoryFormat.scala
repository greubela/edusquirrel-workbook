package it.evadid.sql

import upickle.default.*

/** Current persistence layouts; both store the same typed, timestamped events. */
sealed trait DatabaseHistoryFormat derives ReadWriter {
  def tables: Seq[DatabaseTable]
}

object DatabaseHistoryFormat {
  import MySqlType.*
  private val id = DatabaseColumn("id", BigInt(unsigned = true))
  private val identity = Seq(
    DatabaseColumn("program_id", VarChar(128)), DatabaseColumn("scenario_id", VarChar(128)),
    DatabaseColumn("user_id", VarChar(128)), DatabaseColumn("variable_id", VarChar(255)),
    DatabaseColumn("format", VarChar(128)))
  private val eventColumns = Seq(id, DatabaseColumn("sync_id", BigInt(unsigned = true)),
    DatabaseColumn("event_id", VarChar(36)), DatabaseColumn("timestamp_nano", VarChar(40)),
    DatabaseColumn("serialized_data", Json))

  case object RelationalFormat extends DatabaseHistoryFormat {
    val tables: Seq[DatabaseTable] = Seq(DatabaseTable("sync_context_id", columns = id +: identity)) ++
      Seq("answer_event", "scaffolding_event", "grading_event").map(name => DatabaseTable(name, columns = eventColumns))
  }
  case object BlobFormat extends DatabaseHistoryFormat {
    val tables: Seq[DatabaseTable] = Seq(DatabaseTable("blob_history", columns =
      (id +: identity) :+ DatabaseColumn("history_json", Json)))
  }

  val all: Seq[DatabaseHistoryFormat] = Seq(RelationalFormat, BlobFormat)
}
