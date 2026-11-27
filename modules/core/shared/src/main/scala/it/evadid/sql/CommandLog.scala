package it.evadid.sql

/** Where the backend records one row per received command; the case fixes the written columns. */
sealed trait CommandLog {
  def table: DatabaseTable
}

object CommandLog {
  /** Columns `timestamp, origin, type, payload, userid, ipAddress`; `payload` is the privacy-filtered command. */
  final case class Audit(table: DatabaseTable) extends CommandLog

  /** Columns `timestamp, ipAddress, userid, sessionDataSerialized`: the submitted session, without credentials. */
  final case class SessionSubmission(table: DatabaseTable) extends CommandLog

  val commandLog: CommandLog = Audit(DatabaseTable("commandLog"))
  val syncedLog: CommandLog = Audit(DatabaseTable("syncedLog"))
  val submissionLog: CommandLog = SessionSubmission(DatabaseTable("submissionLog"))
}
