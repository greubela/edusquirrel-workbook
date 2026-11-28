package it.evadid.sql

/** Where the backend records one row per received command. Each case is a fixed table of the setup script with fixed
  * columns, so the backend writes it with a constant prepared statement.
  */
enum CommandLog {
  /** `commandLog`: `timestamp, origin, type, payload, userid, ipAddress`; `payload` is the privacy-filtered command. */
  case CommandAudit
  /** `syncedLog`: the columns of [[CommandAudit]], for history synchronization commands. */
  case SyncAudit
  /** `submissionLog`: `timestamp, ipAddress, userid, sessionDataSerialized`, the submitted session without credentials. */
  case SessionSubmission
}
