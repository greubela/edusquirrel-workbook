package it.evadid.distribution.commandTypes

import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.sql.CommandLog
import it.evadid.workbook.session.SessionData
import upickle.default.*

object SubmissionCommands {

  /** A learner hands in a session. The backend accepts it without an account and records it in `submissionLog`. */
  case class SubmitSessionEvent(sessionData: SessionData) derives ReadWriter

  /** `confirmationSent`: whether the summary reached the learner's own address; the full copy always reached the archive. */
  case class SubmitSessionResponse(submittedInteractions: Int, confirmationSent: Boolean) derives ReadWriter

  /** Diagnostic logs count the submitted histories, never show answers or identities, and are stable when stripped again. */
  private def submissionForLog(payload: ujson.Value): ujson.Value = {
    val fields = payload.objOpt
    val submitted = fields.flatMap(_.get("sessionData")).flatMap(_.objOpt).flatMap(_.get("knownUsageData")).flatMap(_.arrOpt).map(_.size)
    val alreadyRedacted = fields.flatMap(_.get("histories")).flatMap(_.numOpt).map(_.toInt)
    ujson.Obj("redacted" -> true, "histories" -> submitted.orElse(alreadyRedacted).getOrElse(0))
  }

  lazy val submitSessionCommand: ExecutionCommandFactory[SubmitSessionEvent, SubmitSessionResponse] = ExecutionCommandFactory(
    "submit-session-event", privacyFilter = Some(submissionForLog), storeLogIntoTable = Some(CommandLog.SessionSubmission)
  )
}
